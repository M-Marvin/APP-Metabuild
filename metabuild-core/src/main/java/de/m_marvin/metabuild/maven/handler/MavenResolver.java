package de.m_marvin.metabuild.maven.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.net.ssl.HttpsURLConnection;

import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.MavenException;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.ArtifactFile;
import de.m_marvin.metabuild.maven.types.Repository.Credentials;
import de.m_marvin.metabuild.maven.xml.POM;
import de.m_marvin.metabuild.maven.xml.POM.Dependency;
import de.m_marvin.metabuild.maven.xml.POM.Dependency.Scope;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenResolver {
	
	protected Logger logger;
	private TimeUnit timeoutUnit = TimeUnit.SECONDS;
	private long timeout = 5;
	private File cache;
	private boolean refreshLocal;
	private boolean ignoreOptionalDependencies;
	
	public MavenResolver(Logger logger, File localCache) {
		this.logger = new TagLogger(logger, "resolver");
		this.cache = localCache;
	}
	
	public void setRefreshLocal(boolean refreshLocal) {
		this.refreshLocal = refreshLocal;
	}
	
	public void setRemoteTimeout(long timeout, TimeUnit unit) {
		this.timeout = timeout;
		this.timeoutUnit = unit;
	}
	
	public void setIgnoreOptionalDependencies(boolean ignoreOptionalDependencies) {
		this.ignoreOptionalDependencies = ignoreOptionalDependencies;
	}
	
	protected Logger logger() {
		return this.logger;
	}
	
	public boolean resolveGraph(DependencyGraph graph, List<File> artifactOutput, Predicate<Artifact> exclusion) throws MavenException {
		
		for (Artifact artifactGroup : graph.getTransitiveGroups()) {
			
			if (!exclusion.test(artifactGroup)) continue;
			
			if (!graph.isSystemOnly(artifactGroup)) {

				DependencyGraph transitiveGraph = graph.getTransitiveGraph(artifactGroup);
				
				if (transitiveGraph == null) {
					
					transitiveGraph = resolveGraphPOM(graph, artifactGroup.getPOMId());
					
					if (transitiveGraph == null) {
						
						logger().warn("unable to resolve artifact graph: %s", artifactGroup);
						return false;
						
					}
					
					graph.setTransitiveGraph(artifactGroup, transitiveGraph);
					
				}
				
				Predicate<Artifact> transitiveExclusion = graph.getExclusionPredicate(artifactGroup);
				
				if (!resolveGraph(transitiveGraph, artifactOutput, transitiveExclusion)) {
					logger().warn("unable to resolve transitive graphs for artifact: %s", artifactGroup);
					return false;
				}
				
			}
			
			if (artifactOutput != null) {
				
				for (Artifact artifact : graph.getArtifacts(artifactGroup)) {
					
					String systemPath = graph.getSystemPath(artifact);
					if (systemPath != null) {
						
						File systemFile = new File(systemPath);
						if (!systemFile.isFile()) {
							logger().warn("failed to find system artifact: %s", systemFile);
							return false;
						}
						
						artifactOutput.add(systemFile);
						
					} else {

						Repository repository = graph.getTransitiveGraph(artifactGroup).getResolutionRepository();
						File localArtifact = downloadArtifact(repository, artifact);
						if (localArtifact == null) {
							logger().warn("failed to download artifact: %s", artifact);
							return false;
						}
						
						artifactOutput.add(localArtifact);
						
					}
					
				}
				
			}
			
		}
		
		return true;
		
	}
	
	public DependencyGraph resolveGraphPOM(DependencyGraph parent, Artifact pomArtifact) throws MavenException {

		DependencyGraph graph = new DependencyGraph();
		POM pom = resolveFullPOM(parent.getRepositories(), r -> graph.setResolutionRepository(r), pomArtifact);
		
		if (pom == null)
			throw new MavenException("unable to resolve POM artifact: %s", pomArtifact);
		
		Set<String> ndrepo = new HashSet<String>();
		if (pom.repositories != null) {
			for (var r : pom.repositories) {
				String urlStr = pom.fillPoperties(r.url);
				if (!ndrepo.add(urlStr)) continue; // avoid duplicate repositories, pick first in order of import
				try {
					graph.addRepository(new Repository(pom.fillPoperties(r.name), new URL(urlStr)));
				} catch (MalformedURLException e) {
					throw new MavenException("malformed repository URL in fully resolved POM: %s (%s)", urlStr, pomArtifact);
				}
			}
		}
		
		// resolve dependency management version declarations
		Map<Artifact, String> transitiveVersions = new HashMap<Artifact, String>();
		if (pom.dependencyManagement != null) {
			for (var d : pom.dependencyManagement) {
				
				// ignore POM imports here
				if (d.scope == Scope.IMPORT) continue;
				
				Artifact artifact = d.gavce(pom);
				Artifact group = artifact.getGAV().withVersion(null);
				if (transitiveVersions.containsKey(group)) continue; // avoid duplicate entries, pick first in order of import
				transitiveVersions.put(group, artifact.version);
				
			}
		}
		
		// resolve transitive dependencies
		Set<Artifact> nddepend = new HashSet<Artifact>();
		if (pom.dependencies != null) {
			for (var d : pom.dependencies) {
				
				if (d.optional && this.ignoreOptionalDependencies) continue;
				
				Artifact artifact = d.gavce(pom);
				if (artifact.version == null) {
					String declaredVersion = transitiveVersions.get(artifact.getGAV());
					if (declaredVersion == null)
						throw new MavenException("artifact '%s' has no version declared in dependency management!", artifact);
					artifact = artifact.withVersion(declaredVersion);
				}
				
				if (!artifact.hasGAVCE())
					throw new MavenException("artifact '%s' does not define a complete GAVCE coordinate!", artifact);
				
				Artifact group = artifact.getGAV();
				if (!nddepend.add(group)) continue; // avoid duplicate dependencies, pick first in order of import
				
				Set<Artifact> excludes = new HashSet<Artifact>();
				if (d.exclusions != null) {
					for (var e : d.exclusions) {
						excludes.add(e.ga(pom));
					}
				}

				String systemPath = null;
				if (d.scope == Scope.SYSTEM) {
					systemPath = pom.fillPoperties(d.systemPath);
				}
				
				graph.addTransitive(artifact, excludes, systemPath);
				
			}
		}
		
		return graph;
		
	}
	
	public POM resolveFullPOM(Collection<Repository> repositories, Consumer<Repository> pomRepository, Artifact artifact) throws MavenException {
		
		for (Repository repository : repositories) {
			
			logger().info("attempt resolve '%s' on [%s]", artifact, repository.name == null ? repository.baseURL : repository.name);
			
			POM pom = downloadArtifactPOM(repository, artifact);
			
			if (pom == null) continue;
			
			// parse repositories for imports
			List<Repository> repositories2 = new ArrayList<Repository>();
			if (pom.repositories != null) {
				pom.repositories.stream().map(r -> {
					try {
						return new Repository(pom.fillPoperties(r.name), new URL(pom.fillPoperties(r.url)));
					} catch (MalformedURLException e) {
						logger().warn("malformed URL in POM repository: %s", artifact, e);
						return null;
					}
				}).filter(Objects::nonNull).forEach(repositories2::add);
			}
			repositories2.add(repository);
			
			// parse dependency management imports
			if (pom.dependencyManagement != null) {
				
				for (Dependency dependency : pom.dependencyManagement) {
					
					// only care about POM related imports for now
					if (dependency.scope != Scope.IMPORT) continue;
					if (dependency.optional && this.ignoreOptionalDependencies) continue;
					
					Artifact importArtifactPOM = dependency.gavce(pom);
					try {
						POM importPOM = resolveFullPOM(repositories2, r -> {}, importArtifactPOM);
						if (importPOM == null)
							throw new MavenException("not found on repositories: %s", importArtifactPOM);
						pom.importPOM(importPOM, false);
					} catch (MavenException e) {
						throw new MavenException(e, "failed to resolve import POM: %s", importArtifactPOM);
					}
					
				}
				
			}
			
			// parse parent POM
			if (pom.parent != null) {
				
				Artifact importArtifactPOM = pom.parent.gavce(pom);
				try {
					POM importPOM = resolveFullPOM(repositories2, r -> {}, importArtifactPOM);
					if (importPOM == null)
						throw new MavenException("not found on repositories: %s", importArtifactPOM);
					pom.importPOM(importPOM, true);
				} catch (MavenException e) {
					throw new MavenException(e, "failed to resolve parent POM: %s", importArtifactPOM);
				}
				
			}
			
			pomRepository.accept(repository);
			
			logger().debug("-> fully resolved POM: %s", artifact);
			return pom;
			
		}
		
		return null;
		
	}
	
	public POM downloadArtifactPOM(Repository repository, Artifact artifact) throws MavenException {
		
		File localArtifact = downloadArtifact(repository, artifact.getPOMId());
		if (localArtifact == null) return null;
		
		try {
			return POM.fromXML(new FileInputStream(localArtifact));
		} catch (IOException | MavenException e) {
			throw new MavenException(e, "problem when parsing POM: %s", artifact);
		}
		
	}
	
	public File downloadArtifact(Repository repository, Artifact artifact) throws MavenException {
		
		URL artifactURL = repository.artifactURL(artifact, ArtifactFile.DATA);
		File localArtifact = new File(this.cache, artifact.getLocalPath());
		if (localArtifact.isFile() && !this.refreshLocal) return localArtifact;
		localArtifact.getParentFile().mkdirs();
		
		try {

			InputStream onlineStream = openURLConnection(artifactURL, repository.credentials);
			if (onlineStream == null) return null;
			OutputStream localStream = new FileOutputStream(localArtifact);
			
			for (ArtifactFile file : ArtifactFile.checksums()) {
				
				try {
					URL checksumURL = repository.artifactURL(artifact, file);
					MessageDigest digest = MessageDigest.getInstance(file.getAlgorithm());
					InputStream checksumStream = openURLConnection(checksumURL, repository.credentials);
					
					// checksum not supported on repository, skip
					if (checksumStream == null) continue;
					
					String checksumStr = new String(checksumStream.readAllBytes()).split("\\W")[0];
					try {
						byte[] onlineChecksum = HexFormat.of().parseHex(checksumStr);
						checksumStream.close();
						
						byte[] buffer = new byte[1024];
						int len = 0;
						while ((len = onlineStream.read(buffer)) > 0) {
							digest.update(buffer, 0, len);
							localStream.write(buffer, 0, len);
						}
						byte[] localChecksum = digest.digest();
						
						onlineStream.close();
						localStream.close();
						
						// verify checksum
						if (Arrays.compare(localChecksum, onlineChecksum) != 0)
							throw new MavenException("artifact checksum error: online %s != local %s", HexFormat.of().formatHex(onlineChecksum), HexFormat.of().formatHex(localChecksum));
						
						return localArtifact;
					} catch (IllegalArgumentException e) {
						localStream.close();
						localArtifact.delete();
						throw new MavenException(e, "checksum error, received malformed checksum: '%s'", checksumStr);
					}
					
				} catch (NoSuchAlgorithmException e) {
					// checksum not supported on local system, skip
					continue;
				} catch (IOException e) {
					// checksum or transfer error
					localStream.close();
					localArtifact.delete();
					throw new MavenException(e, "failed to transfer artifact or checksum: %s", artifact);
				}
				
			}
			
			// no checksum supported, just transfer bytes
			try {
				onlineStream.transferTo(localStream);
				onlineStream.close();
				localStream.close();
				
				return localArtifact;
			} catch (IOException e) {
				// transfer error
				localStream.close();
				localArtifact.delete();
				throw new MavenException(e, "failed to transfer artifact: %s", artifact);
			}
			
		} catch (FileNotFoundException e) {
			localArtifact.delete();
			throw new MavenException(e, "unable to create local artifact file: %s / %s", artifact, localArtifact);
		} catch (MavenException e) {
			localArtifact.delete();
			throw e;
		} catch (Throwable e) {
			localArtifact.delete();
			throw new MavenException(e, "unknown internal error when processing POM artifact: %s", artifact, localArtifact);
		}
		
	}
	
	private InputStream openURLConnection(URL url, Credentials credentials) throws MavenException {
		
		logger().debug("access URL: %s", url);
		
		try {
			
			URLConnection connection = url.openConnection();
			connection.setReadTimeout((int) this.timeoutUnit.toMillis(this.timeout));
			
			if (credentials != null) {
				if (credentials.token() != null)
					connection.setRequestProperty("Authorization", "Bearer " + credentials.bearer());
				if (connection instanceof HttpsURLConnection httpsConnection && credentials.username() != null && credentials.password() != null)
					httpsConnection.setAuthenticator(credentials.authenticator());
			}
			
			if (connection instanceof HttpURLConnection httpConnection) {
				httpConnection.setRequestMethod("GET");
				int rcode = httpConnection.getResponseCode();
				
				if (rcode != 200) {
					logger().debug("not found: %d %s", rcode, httpConnection.getResponseMessage());
					httpConnection.disconnect();
					return null;
				}
			}
			
			return connection.getInputStream();
			
		} catch (FileNotFoundException e) {
			logger().debug("unable to get resource: %s", e.getMessage());
			return null;
		} catch (IOException e) {
			throw new MavenException(e, "exception while transfering remote artifact: %s", url);
		}
		
	}
	
}
