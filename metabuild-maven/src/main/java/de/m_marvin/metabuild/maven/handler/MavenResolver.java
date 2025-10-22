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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.Artifact.DataLevel;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveEntry;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveGroup;
import de.m_marvin.metabuild.maven.types.DependencyScope;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.ArtifactFile;
import de.m_marvin.metabuild.maven.types.Repository.Credentials;
import de.m_marvin.metabuild.maven.xml.POM;
import de.m_marvin.metabuild.maven.xml.POM.Dependency;
import de.m_marvin.metabuild.maven.xml.POM.Dependency.Scope;
import de.m_marvin.metabuild.maven.xml.VersionMetadata;
import de.m_marvin.simplelogging.api.Logger;

public class MavenResolver {
	
	protected final Logger logger;
	private final File localCache;
	private TimeUnit remoteTimeoutUnit = TimeUnit.SECONDS;
	private long remoteTimeout = 5;
	private TimeUnit metadataExpirationUnit = TimeUnit.SECONDS;
	private long metadataExpiration = 60;
	private ResolutionStrategy resolutionStrategy = ResolutionStrategy.REMOTE;
	private boolean ignoreOptionalDependencies = false;
	private Consumer<String> statusCallback = s -> {};
	private ZonedDateTime snapshotTimestamp = null;
	private boolean autoIncludeSources = false;
	
	public static enum ResolutionStrategy {
		OFFLINE,REMOTE,FORCE_REMOTE;
	}
	
	public MavenResolver(Logger logger, File localCache) {
		this.logger = logger;
		this.localCache = localCache;
	}
	
	public void setStatusCallback(Consumer<String> statusCallback) {
		this.statusCallback = statusCallback;
	}
	
	/**
	 * If true, automatically adds the source classifier transitive for all jar dependencies
	 */
	public void setAutoIncludeSources(boolean autoIncludeSources) {
		this.autoIncludeSources = autoIncludeSources;
	}
	
	/**
	 * Sets the timeout for remote connections
	 */
	public void setRemoteTimeout(long timeout, TimeUnit unit) {
		this.remoteTimeout = timeout;
		this.remoteTimeoutUnit = unit;
	}
	
	/**
	 * Sets the expiration time for metadata in the local cache
	 */
	public void setMetadataExpiration(long expiration, TimeUnit unit) {
		this.metadataExpiration = expiration;
		this.metadataExpirationUnit = unit;
	}
	
	/**
	 * Sets the resolution strategy to use.<br>
	 * - OFFLINE -> Acquire artifacts only from local cache<br>
	 * - REMOTE -> Acquire artifacts from remote repository if not available in local cache<br>
	 * - FORCE_REMOTE -> Acquire all artifacts from remote repository, replacing the ones in the local cache<br>
	 */
	public void setResolutionStrategy(ResolutionStrategy resolutionStrategy) {
		this.resolutionStrategy = resolutionStrategy;
	}
	
	/**
	 * Tells the resolver to ignore all transitive dependencies marked as optional
	 */
	public void setIgnoreOptionalDependencies(boolean ignoreOptionalDependencies) {
		this.ignoreOptionalDependencies = ignoreOptionalDependencies;
	}
	
	/**
	 * Returns the timestamp of the latest downloaded SNAPSHOT artifact
	 * @return the timestamp of the artifact, or null if no such artifact was downloaded or the timestamp could not be optained
	 */
	public ZonedDateTime getLastSnapshotVersion() {
		return this.snapshotTimestamp;
	}
	
	protected Logger logger() {
		return this.logger;
	}
	
	public File getLocalCache() {
		return localCache;
	}
	
	/**
	 * Attempts to resolve and download all the artifacts for the supplied list of dependency versions using the resolved graph.<br>
	 * The file path to the downloaded artifacts is put into the supplied list.
	 * @param graph The graph to resolve the list of dependency versions against
	 * @param dependencyVersions The list of dependency versions to resolve and download
	 * @param artifactOutput The list to fill with the artifact file paths
	 * @param completitionList An list which is filled with all Artifacts which have been resolved successfully, can be null, items on this list will not be resolved again
	 * @param artifactScope The scope to limit the resolution to
	 * @return true if all dependencies could be resolved and downloaded successfully, false is returned as soon as one resolution fails
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts
	 */
	public boolean downloadArtifacts(DependencyGraph graph, Collection<Artifact> dependencyVersions, List<File> artifactOutput, List<Artifact> completitionList, DependencyScope artifactScope) throws MavenException {
		
		if (completitionList == null)
			completitionList = new ArrayList<Artifact>();
		
		for (TransitiveGroup transitiveGroup : graph.getTransitiveGroups()) {
			
			if (!artifactScope.includes(transitiveGroup.scope)) continue;
			
			if (!dependencyVersions.contains(transitiveGroup.group)) continue;
			
			for (TransitiveEntry transitive : transitiveGroup.artifacts) {
				
				this.statusCallback.accept("resolving > " + transitive.artifact);
				
				if (transitiveGroup.scope == Scope.SYSTEM) {
					
					// system dependencies are expected to be available on the system
					File systemFile = FileUtility.absolute(new File(POM.fillPropertiesStatic(transitive.systemPath)));
					if (!systemFile.exists()) {
						logger().warn("failed to find system artifact: %s", systemFile);
						
						// ignore optional dependencies if failed to resolve, just warn about it
						if (!transitive.optional)
							return false;
						else
							logger().warn("artifact marked as optional, ignore");
					}
					
					if (systemFile.exists() && !artifactOutput.contains(systemFile))
						artifactOutput.add(systemFile);
					
				} else {
					
					Repository repository = transitiveGroup.graph.getResolutionRepository();
					
					ResolutionStrategy strategy = this.resolutionStrategy;
					
					// avoid downloading the same artifact multiple times
					if (completitionList.contains(transitive.artifact)) strategy = strategy == ResolutionStrategy.FORCE_REMOTE ? ResolutionStrategy.REMOTE : ResolutionStrategy.OFFLINE;

					// ignore optional dependencies if asked to
					if (transitive.optional && this.ignoreOptionalDependencies) continue;
					
					File localArtifact = downloadArtifact(repository, transitive.artifact, strategy);
					if (localArtifact == null) {
						logger().warn("failed to download artifact: %s", transitive.artifact);
						
						// ignore optional dependencies if failed to resolve, just warn about it
						if (!transitive.optional)
							return false;
						else
							logger().warn("artifact marked as optional, ignore");
					}
					
					if (localArtifact != null && !artifactOutput.contains(localArtifact))
						artifactOutput.add(localArtifact);
					
				}

				completitionList.add(transitive.artifact);
				
			}
			
			if (transitiveGroup.graph != null && !downloadArtifacts(transitiveGroup.graph, dependencyVersions, artifactOutput, completitionList, artifactScope)) return false;
			
		}
		
		return true;
	}
	
	/**
	 * Attempt to resolve all transitive dependencies to a full dependency graph.<br>
	 * Fills the supplied map with all effectively imported transitive dependency versions and their hierarchical position in the graph three.
	 * @param graph The top level graph containing the first dependencies to resolve
	 * @param exclusion An exclusion predicate for direct transitive artifacts to ignore
	 * @param dependencyVersions The map to fill with the effectively imported transitive dependency versions
	 * @param priority The priority value which represents the supplied graph in the dependency map, increased by one for each step deeper into the sub-graph tree
	 * @param artifactScope The scope for which to collect the local artifacts, may be null
	 * @return true if all dependencies where successfully resolved, false otherwise
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public boolean resolveGraph(DependencyGraph graph, Predicate<Artifact> exclusion, Map<Artifact, Integer> dependencyVersions, int priority, DependencyScope artifactScope) throws MavenException {
		
		// attempt to resolve all transitive dependency groups of this graph
		for (TransitiveGroup transitiveGroup : graph.getTransitiveGroups()) {
			
			// ignore excluded transitive dependencies
			if (exclusion.test(transitiveGroup.group)) continue;
			
			this.statusCallback.accept("resolving graph > " + transitiveGroup.group);
			
			// do not attempt graph resolution for system dependencies
			if (transitiveGroup.scope != Scope.SYSTEM) {
				
				if (transitiveGroup.graph == null) {
					
					transitiveGroup.graph = resolveGraphPOM(graph, transitiveGroup.group.getPOMId(), transitiveGroup.scope);
					
					if (transitiveGroup.graph == null) {
						
						logger().warn("unable to resolve artifact graph for group: %s (scope %s)", transitiveGroup.group, transitiveGroup.scope);
						
						// check if an another version of transitiveGroup.group is already present in dependencyVersions with an higher or equal priority
						// this prevents the build from failing because of an missing outdated version even if it is replaced in with an newer one higher up in the dependency tree
						
						for (Entry<Artifact, Integer> definedGroup : dependencyVersions.entrySet()) {
							if (definedGroup.getValue() > priority) continue;
							if (	definedGroup.getKey().groupId.equals(transitiveGroup.group.groupId) &&
									definedGroup.getKey().artifactId.equals(transitiveGroup.group.artifactId)) {
								logger().warn("missing resolution ignored, higher priority defined in dependency tree: %s", definedGroup.getKey());
								return true;
							}
						}
						
						return false;
						
					}
					
				}
				
				// get exclusion predicate for transitive dependencies
				Predicate<Artifact> transitiveExclusion = transitiveGroup.excludes != null ? transitiveGroup.excludes::contains : g -> false;
				
				// attempt to resolve child graph of transitive dependency
				if (!resolveGraph(transitiveGroup.graph, transitiveExclusion, dependencyVersions, priority + 1, artifactScope)) {
					logger().warn("unable to resolve transitive graphs for artifact group: %s (scope %s)", transitiveGroup.group, transitiveGroup.scope);
					return false;
				}
				
			}
			
			if (!artifactScope.includes(transitiveGroup.scope)) continue;
			
			// check the hierarchical position of this transitive import and add it to the map if required
			Artifact replacedVersion = null;
			for (Artifact definedGroup : dependencyVersions.keySet()) {
				if (	definedGroup.groupId.equals(transitiveGroup.group.groupId) &&
						definedGroup.artifactId.equals(transitiveGroup.group.artifactId)) {
					if (	!definedGroup.baseVersion.equals(transitiveGroup.group.baseVersion) &&
							dependencyVersions.get(definedGroup) > priority)
						replacedVersion = definedGroup;
					break;
				}
			}
			if (replacedVersion != null) {
				dependencyVersions.remove(replacedVersion);
			}
			Artifact replacingVersion = null;
			for (Artifact definedGroup : dependencyVersions.keySet()) {
				if (	definedGroup.groupId.equals(transitiveGroup.group.groupId) &&
						definedGroup.artifactId.equals(transitiveGroup.group.artifactId)) {
					if (	!definedGroup.baseVersion.equals(transitiveGroup.group.baseVersion) &&
							dependencyVersions.get(definedGroup) <= priority)
						replacingVersion = definedGroup;
					break;
				}
			}
			if (replacingVersion == null) {
				dependencyVersions.put(transitiveGroup.group, priority);
			}
			
		}
		
		return true;
		
	}
	
	/**
	 * Attempt to resolve the POM of the supplied POM artifact and parse it to an transitive dependency graph.
	 * @param parent The parent graph from which this POM is a transitive dependency
	 * @param pomArtifact The POM artifact coordinates
	 * @param scope The scope under which the transitive are imported
	 * @return the parsed dependency graph or null if the resolution failed
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public DependencyGraph resolveGraphPOM(DependencyGraph parent, Artifact pomArtifact, Scope scope) throws MavenException {
		
		// create child graph for this POM
		DependencyGraph graph = new DependencyGraph(parent.getRepositories(), Collections.emptyList()); // PARENT REPOSITORY FORWARDING 1
		
		// attempt to resolve full POM
		POM pom = resolveFullPOM(parent.getRepositories(), r -> graph.setResolutionRepository(r), pomArtifact);
		
		if (pom == null) return null;
		
		// resolve additional repositories
		Set<String> ndrepo = new HashSet<String>();
		if (pom.repositories != null) {
			for (var r : pom.repositories.repository) {
				String urlStr = pom.fillPoperties(r.url);
				if (!ndrepo.add(urlStr)) continue; // avoid duplicate repositories, pick first in order of import
				try {
					graph.addRepository(new Repository(pom.fillPoperties(r.name), new URI(urlStr).toURL()));
				} catch (URISyntaxException | MalformedURLException e) {
					throw new MavenException("malformed repository URL in fully resolved POM: %s (%s)", urlStr, pomArtifact);
				}
			}
		}
		
		// resolve dependency management version declarations
		Map<Artifact, String> transitiveVersions = new HashMap<Artifact, String>();
		if (pom.dependencyManagement != null) {
			for (var d : pom.dependencyManagement.dependency) {
				
				// get the scope the imported transitive will have in this graph
				Scope effectiveScope = scope.effective(d.scope);
				
				// ignore dependencies which should be omitted
				if (effectiveScope == null) continue;
				
				// ignore optional dependencies if asked to
				if (d.optional && this.ignoreOptionalDependencies) continue;
				
				Artifact artifact = d.gavce();
				Artifact group = artifact.getGAV().withVersion(null);
				if (transitiveVersions.containsKey(group)) continue; // avoid duplicate entries, pick first in order of import
				transitiveVersions.put(group, artifact.baseVersion);
				
			}
		}
		
		// resolve transitive dependencies
		Set<Artifact> nddepend = new HashSet<Artifact>();
		if (pom.dependencies != null) {
			for (var d : pom.dependencies.dependency) {
				
				// get the scope the imported transitive will have in this graph
				Scope effectiveScope = scope.effective(d.scope);
				
				// ignore dependencies which should be omitted
				if (effectiveScope == null) continue;
				
				// ignore optional dependencies if asked to
				if (d.optional && this.ignoreOptionalDependencies) continue;
				
				// resolve full transitive artifact coordinates
				Artifact artifact = d.gavce();
				if (artifact.baseVersion == null) {
					String declaredVersion = transitiveVersions.get(artifact.getGAV());
					if (declaredVersion == null)
						throw new MavenException("dependency inconsistencies, artifact '%s' has no version declared in dependency management!", artifact);
					artifact = artifact.withVersion(declaredVersion);
				}
				
				if (!artifact.hasGAVCE())
					throw new MavenException("dependency inconsistencies, artifact '%s' does not define a complete GAVCE coordinate!", artifact);
				
				if (!nddepend.add(artifact)) continue; // avoid duplicate dependencies, pick first in order of import
				
				// parse exclusion filters
				Set<Artifact> excludes = new HashSet<Artifact>();
				if (d.exclusions != null) {
					for (var e : d.exclusions.exclusion) {
						excludes.add(e.ga());
					}
				}
				
				String systemPath = null;
				if (d.scope == Scope.SYSTEM) {
					systemPath = pom.fillPoperties(d.systemPath);
				}
				
				graph.addTransitive(effectiveScope, artifact, excludes, systemPath, d.optional);
				
				if (this.autoIncludeSources && (artifact.extension.equals("jar") || artifact.extension.equals("")) && !artifact.classifier.equals("sources")) {
					try {
						Artifact sources = artifact.withClassifier("sources", artifact.extension);
						graph.addTransitive(effectiveScope, sources, excludes, systemPath, true);
					} catch (MavenException e) {}
				}
				
			}
		}
		
		return graph;
		
	}
	
	/**
	 * Resolves the POM of the supplied artifact, including parent POMs and imports.
	 * @param repositories The repositories to attempt to resolve the POM on
	 * @param pomRepository The callback for reporting back where the POM was successfully resolved
	 * @param artifact The artifact  The artifact from which to get the POM for
	 * @return the resolved POM or null if the POM could not be resolved on the supplied repositories and was not available in cache
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public POM resolveFullPOM(Collection<Repository> repositories, Consumer<Repository> pomRepository, Artifact artifact) throws MavenException {
		
		for (Repository repository : repositories) {
			
			// don't print message if resolving from cache, might be misleading since no actual repositroy is contacted
			if (this.resolutionStrategy != ResolutionStrategy.OFFLINE)
				logger().info("attempt resolve '%s' on [%s]", artifact, repository.name == null ? repository.baseURL : repository.name);
			
			POM pom = downloadArtifactPOM(repository, artifact);
			if (pom == null) continue;
			
			// parse repositories for imports
			List<Repository> repositories2 = new ArrayList<Repository>();
			if (pom.repositories != null) {	
				pom.repositories.repository.stream().map(r -> {
					try {
						return new Repository(pom.fillPoperties(r.name), new URI(pom.fillPoperties(r.url)).toURL());
					} catch (MalformedURLException | URISyntaxException e) {
						logger().warn("malformed URL in POM repository: %s", artifact, e);
						return null;
					}
				}).filter(Objects::nonNull).forEach(repositories2::add);
			}
			//repositories2.add(repository);
			repositories2.addAll(repositories); // PARENT REPOSITORY FORWARDING 2
			
			// parse dependency management imports
			if (pom.dependencyManagement != null) {
				
				for (Dependency dependency : pom.dependencyManagement.dependency) {
					
					// only care about POM related imports for now
					if (dependency.scope != Scope.IMPORT) continue;
					if (dependency.optional && this.ignoreOptionalDependencies) continue;
					
					Artifact importArtifactPOM = dependency.gavce();
					try {
						POM importPOM = resolveFullPOM(repositories2, r -> {}, importArtifactPOM);
						if (importPOM == null)
							if (dependency.optional)
								logger().warn("unable to resolve optional dependency POM: %s", importArtifactPOM);
							else
								throw new MavenException("POM resolution inconsistencies, import not found on repositories: %s", importArtifactPOM);
						else
							pom.importPOM(importPOM, false);
					} catch (MavenException e) {
						if (dependency.optional)
							logger().warn("unable to fully resolve optional dependency POM: %s", importArtifactPOM);
						else
							throw new MavenException(e, "POM resolution inconsistencies, failed to resolve import POM: %s", importArtifactPOM);
					}
					
				}
				
			}
			
			// parse parent POM
			if (pom.parent != null) {
				
				Artifact importArtifactPOM = pom.parent.gavce();
				try {
					POM importPOM = resolveFullPOM(repositories2, r -> {}, importArtifactPOM);
					if (importPOM == null)
						throw new MavenException("POM resolution inconsistencies, parent not found on repositories: %s", importArtifactPOM);
					pom.importPOM(importPOM, true);
				} catch (MavenException e) {
					throw new MavenException(e, "POM resolution inconsistencies, failed to resolve parent POM: %s", importArtifactPOM);
				}
				
			}
			
			pomRepository.accept(repository);
			
			logger().info("-> fully resolved POM: %s", artifact);
			
			return pom;
			
		}
		
		return null;
		
	}
	
	/**
	 * Attempts to download the POM for the supplied artifact from the remote repository to the cache if required, and parses it from XML<br>
	 * Snapshot version resolution is handled automatically.
	 * @param repository The remote repository from which to download the file if required
	 * @param artifact The artifact to acquire, this must not necccessarly point to the POM itself
	 * @return The path to the acquired remote file in the local cache, or null if the remote file was not acquired
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public POM downloadArtifactPOM(Repository repository, Artifact artifact) throws MavenException {
		
		File localArtifact = downloadArtifact(repository, artifact.getPOMId(), this.resolutionStrategy);
		if (localArtifact == null) return null;
		
		try {
			return POM.fromXML(new FileInputStream(localArtifact));
		} catch (IOException | MavenException e) {
			throw new MavenException(e, "problem when parsing POM: %s", artifact);
		}
		
	}
	
	protected static final Pattern TIMESTAMP_FORMAT = Pattern.compile("(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)-(\\d\\d)(\\d\\d)(\\d\\d)");
	
	/**
	 * Attempts to download the artifact from the remote repository to the cache if required, and returns the local cache file<br>
	 * Snapshot version resolution is handled automatically.
	 * @param repository The remote repository from which to download the file if required
	 * @param artifact The artifact to acquire
	 * @param strategy The resolution strategy to apply
	 * @return The path to the acquired remote file in the local cache, or null if the remote file was not acquired
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public File downloadArtifact(Repository repository, Artifact artifact, ResolutionStrategy strategy) throws MavenException {
		
		// if snapshot artifact, metadata resolution required first
		if (artifact.isSnapshot() && !artifact.isSnapshotDefined()) {
			
			// download snapshot metadata or pull from cache if not yet expired
			File snapshotMetadataFile = downloadArtifact(repository, artifact, DataLevel.META_VERSION, strategy);
			if (snapshotMetadataFile == null) return null;
			
			try {
				
				// parse metadata XML
				VersionMetadata snapshotMetadata = VersionMetadata.fromXML(new FileInputStream(snapshotMetadataFile));
				if (snapshotMetadata.versioning == null)
					throw new MavenException("malformed snapshot mave-metadata: %s", artifact);
				
				// get latest snapshot version
				String concreteVersion = snapshotMetadata.versioning.snapshot.timestamp + "-" + snapshotMetadata.versioning.snapshot.buildNumber;
				artifact = artifact.withSnapshotVersion(concreteVersion);
				
				// parse snapshot timestamp
				Matcher timestamp = TIMESTAMP_FORMAT.matcher(artifact.version);
				if (timestamp.find()) {
					this.snapshotTimestamp = ZonedDateTime.of(
							Integer.parseInt(timestamp.group(1)), 
							Integer.parseInt(timestamp.group(2)), 
							Integer.parseInt(timestamp.group(3)), 
							Integer.parseInt(timestamp.group(4)), 
							Integer.parseInt(timestamp.group(5)), 
							Integer.parseInt(timestamp.group(6)), 
							0, 
							ZoneOffset.UTC);
				}
				
			} catch (IOException | MavenException e) {
				throw new MavenException(e, "problem when parsing maven-metadata: %s", artifact);
			}
			
		}
		
		return downloadArtifact(repository, artifact, DataLevel.ARTIFACT, strategy);
		
	}

	/**
	 * Attempts to download the artifact or metadata from the remote repository to the cache if required, and returns the local cache file<br>
	 * If the artifact coordinates are snapshot coordinates, the concrete timestamped version has to be resolved for this to work.
	 * @param repository The remote repository from which to download the file if required
	 * @param artifact The artifact to acquire
	 * @param dataLevel The data level, this indicates if the actual artifact or one of the three metadata levels should be acquired
	 * @return The path to the acquired remote file in the local cache, or null if the remote file was not acquired
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public File downloadArtifact(Repository repository, Artifact artifact, DataLevel dataLevel) throws MavenException {
		return downloadArtifact(repository, artifact, this.resolutionStrategy);
	}
	
	/**
	 * Attempts to download the artifact or metadata from the remote repository to the cache if required, and returns the local cache file<br>
	 * If the artifact coordinates are snapshot coordinates, the concrete timestamped version has to be resolved for this to work.
	 * @param repository The remote repository from which to download the file if required
	 * @param artifact The artifact to acquire
	 * @param dataLevel The data level, this indicates if the actual artifact or one of the three metadata levels should be acquired
	 * @param strategy The resolution strategy to apply
	 * @return The path to the acquired remote file in the local cache, or null if the remote file was not acquired
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public File downloadArtifact(Repository repository, Artifact artifact, DataLevel dataLevel, ResolutionStrategy strategy) throws MavenException {
		
		// assemble remote URL and local path
		File localArtifact = new File(this.localCache, repository.getCacheFolder() +"/" + artifact.getLocalPath(dataLevel));
		
		// check if file already exists in local cache
		if (strategy != ResolutionStrategy.FORCE_REMOTE) {
			if (localArtifact.isFile()) {
				if (!dataLevel.isMetadata()) return localArtifact;
				
				// for metadata, check expiration time
				try {
					BasicFileAttributes atr = Files.readAttributes(Paths.get(localArtifact.getPath()), BasicFileAttributes.class);
					long lastUpdate = System.currentTimeMillis() - atr.lastModifiedTime().toMillis();
					if (lastUpdate < this.metadataExpirationUnit.toMillis(this.metadataExpiration))
						return localArtifact;
				} catch (IOException e) {}
				
				// if expired, continue with remote download, even in OFFLINE mode
			}
		}
		
		// if no data in cache and in OFFLINE mode and not metadata, abort resolution, don't download anything
		if (!localArtifact.isFile() && this.resolutionStrategy == ResolutionStrategy.OFFLINE && !dataLevel.isMetadata()) return null;
		
		try {

			// get remote URL
			URL artifactURL = repository.artifactURL(artifact, dataLevel, ArtifactFile.DATA);
			
			// get remote connection stream
			InputStream onlineStream = openURLConnection(artifactURL, repository.credentials);
			
			// if remote connection failed, check if cache is still available to return
			if (onlineStream == null) return (localArtifact.isFile() && this.resolutionStrategy != ResolutionStrategy.FORCE_REMOTE) ? localArtifact : null;
			
			// get local cache file stream
			localArtifact.getParentFile().mkdirs();
			OutputStream localStream = new FileOutputStream(localArtifact);
			
			// attempt to request one of the checksums from remote repository
			for (ArtifactFile file : ArtifactFile.checksums()) {
				
				try {
					// acquire checksum stream and checksum algorithm
					URL checksumURL = repository.artifactURL(artifact, dataLevel, file);
					MessageDigest digest = MessageDigest.getInstance(file.getAlgorithm());
					InputStream checksumStream = openURLConnection(checksumURL, repository.credentials);
					
					// checksum not supported on repository, skip
					if (checksumStream == null) continue;
					
					// parse checksum from stream
					String checksumStr = new String(checksumStream.readAllBytes()).split("\\W")[0];
					try {
						byte[] onlineChecksum = HexFormat.of().parseHex(checksumStr);
						checksumStream.close();
						
						// transfer bytes from remote repository, compute hash for checksum check
						byte[] buffer = new byte[1024];
						int len = 0;
						while ((len = onlineStream.read(buffer)) > 0) {
							digest.update(buffer, 0, len);
							localStream.write(buffer, 0, len);
						}
						byte[] localChecksum = digest.digest();
						
						// terminate local cache and remote repository streams
						onlineStream.close();
						localStream.close();
						
						// verify checksum
						if (Arrays.compare(localChecksum, onlineChecksum) != 0)
							throw new MavenException("artifact checksum error: online %s != local %s > %s", HexFormat.of().formatHex(onlineChecksum), HexFormat.of().formatHex(localChecksum), checksumURL.toString());
						
						return localArtifact;
					} catch (IllegalArgumentException e) {
						localStream.close();
						localArtifact.delete();
						throw new MavenException(e, "checksum error, received malformed checksum: '%s' > %s", checksumStr, checksumURL.toExternalForm());
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
	
	/**
	 * Opens a connection to the remote URL, handling authorization using the supplied credentials
	 * @param url The remote URL to connect to
	 * @param credentials The credentials for authorization (may be null)
	 * @return An input stream to the remote URL, or null if the connection could not be established
	 * @throws MavenException if an unexpected error occurred which prevents further resolving of other artifacts or repositories
	 */
	public InputStream openURLConnection(URL url, Credentials credentials) throws MavenException {
		
		logger().debug("access URL: %s", url);
		
		try {
			
			// open remote connection and configure timeouts
			URLConnection connection = url.openConnection();
			connection.setReadTimeout((int) this.remoteTimeoutUnit.toMillis(this.remoteTimeout));
			
			// apply credentials if available
			if (credentials != null) {
				if (credentials.token() != null)
					connection.setRequestProperty("Authorization", "Bearer " + credentials.bearer());
				if (connection instanceof HttpsURLConnection httpsConnection && credentials.username() != null && credentials.password() != null)
					httpsConnection.setAuthenticator(credentials.authenticator());
			}
			
			// if HTTP connection, check header status codes
			if (connection instanceof HttpURLConnection httpConnection) {
				httpConnection.setRequestMethod("GET");
				int rcode = httpConnection.getResponseCode();
				
				if (rcode != 200) {
					logger().debug("not found: %d %s", rcode, httpConnection.getResponseMessage());
					httpConnection.disconnect();
					return null;
				}
			}
			
			// return connection stream
			return connection.getInputStream();
			
		} catch (FileNotFoundException e) {
			logger().debug("unable to get resource: %s", e.getMessage());
			return null;
		} catch (IOException e) {
			throw new MavenException(e, "exception while connecting to remote artifact: %s", url);
		}
		
	}
	
}
