package de.m_marvin.metabuild.java.maven;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import de.m_marvin.metabuild.core.util.SkipOptional;
import de.m_marvin.metabuild.java.maven.MavenResolver.MavenRepository.Credentials;
import de.m_marvin.metabuild.java.maven.MavenResolver.POM.Scope;
import de.m_marvin.simplelogging.api.Logger;

public class MavenResolver {
	
	private File cache;
	private Logger logger;
	private TimeUnit timeoutUnit = TimeUnit.SECONDS;
	private long timeout = 5;
	
	/* If set to true, does not attempt to request artifacts that are not listed in the repos maven-meta */
	public static boolean strictMavenMeta = false;
	/* If set to true, all artifacts are attempted to be verified by requesting an hash value from the repository */
	public static boolean strictHashVerify = false;
	
	private final DocumentBuilderFactory factory;
	private final DocumentBuilder builder;
	
	public MavenResolver(File cacheDir, Logger logger) throws Exception {
		this.cache = cacheDir;
		this.logger = logger;
		
		try {
			this.factory = DocumentBuilderFactory.newInstance();
			this.builder = this.factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new Exception("failed to instanciate dependency resolver!", e);
		}
	}
	
	public File getCache() {
		return cache;
	}
	
	public DocumentBuilder getXMLParser() {
		return builder;
	}
	
	public Logger logger() {
		return this.logger;
	}
	
	public static record MavenRepository(String id, String url, Credentials credentials) {
		public static record Credentials(Supplier<String> username, Supplier<String> password, Supplier<String> token) {
			
			public Credentials(Supplier<String> username, Supplier<String> password) {
				this(username, password, null);
			}

			public Credentials(Supplier<String> token) {
				this(null, null, token);
			}
			
			public Authenticator authenticator() {
				return new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(Credentials.this.username.get(), Credentials.this.password.get().toCharArray());
					}
				};
			}
			
			public String bearer() {
				return this.token.get();
			}
			
		}
		
		public URL getArtifactURL(String group, String artifact, String file) throws MalformedURLException, URISyntaxException {
			return new URI(this.url + "/" + group.replace('.', '/') + "/" + artifact + "/" + file).toURL();
		}
		
		public URL getVersionURL(String group, String artifact, String version, String file) throws MalformedURLException, URISyntaxException {
			return new URI(this.url + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + file).toURL();
		}
		
		public String name() {
			return id == null ? url : id;
		}
		
		public static MavenRepository mavenLocal() {
			return new MavenRepository(
					"Maven Local",
					"file:///" + System.getProperty("user.home").replace('\\', '/') + "/.m2/repository",
					null
			);
		}
		
	}
	
	private final List<MavenRepository> repositories = new ArrayList<>();
	
	public void addRepository(MavenRepository repository) {
		if (!this.repositories.contains(repository)) this.repositories.add(repository);
	}
	
	public List<MavenRepository> getRepositories() {
		return repositories;
	}
	
	public static final Pattern DEPENDENCY_SEGMENT_PATTERN = Pattern.compile("[a-z0-9\\-\\._]+");
	public static final Pattern DEPENDENCY_STRING_PATTERN = Pattern.compile("(?<group>[a-z0-9\\-\\._]+):(?<artifact>[a-z0-9\\-\\._]+):(?<version>[a-z0-9\\-\\._]+)");
	
	public Optional<POM> resolveStr(String str, String... configurations) {
		Matcher m = DEPENDENCY_STRING_PATTERN.matcher(str);
		if (!m.matches()) {
			logger().warn("invalid dependency format: %s", str);
			return Optional.empty();
		}
		return resolve(m.group("group"), m.group("artifact"), m.group("version"), configurations);
	}
	
	public Optional<POM> resolve(String group, String artifact, String version, String... configurations) {
		if (!DEPENDENCY_SEGMENT_PATTERN.matcher(group).matches()) {
			logger().warn("invalid dependency group format: %s", group);
			return Optional.empty();
		}
		if (!DEPENDENCY_SEGMENT_PATTERN.matcher(artifact).matches()) {
			logger().warn("invalid dependency artifact format: %s", artifact);
			return Optional.empty();
		}
		if (!DEPENDENCY_SEGMENT_PATTERN.matcher(version).matches()) {
			logger().warn("invalid dependency version format: %s", version);
			return Optional.empty();
		}
		
		if (configurations != null && configurations.length > 0 && configurations[0] == null) configurations = null;
		
		for (MavenRepository repository : this.repositories) {

			logger().info("try resolving on [%s]: %s:%s:%s", repository.name(), group, artifact, version);
			
			Optional<POM> pom = tryResolve(repository, group, artifact, version, configurations);
			if (pom.isPresent()) {
				
				logger().info("-> found dependency: %s:%s:%s on [%s]", group, artifact, version, repository.id);
				
				return pom;
				
			}
			
		}
		
		return Optional.empty();
	}
	
	protected Optional<InputStream> queryFile(URL url, File cache, Credentials credentials) throws IOException {
		
		logger().debug("request from url: %s", url);
		URLConnection connection = url.openConnection();
		connection.setReadTimeout((int) this.timeoutUnit.toMillis(this.timeout));
		
		if (connection instanceof HttpsURLConnection httpConnection) {
			httpConnection.setRequestMethod("GET");
			if (credentials != null) {
				if (credentials.username() != null && credentials.password() != null)
					httpConnection.setAuthenticator(credentials.authenticator());
				if (credentials.token() != null)
					httpConnection.setRequestProperty("Authorization", "Bearer "+credentials.bearer());
			}
			int rcode = httpConnection.getResponseCode();
			
			if (rcode == 404) {
				logger().debug("not found: 404 %s", httpConnection.getResponseMessage());
				httpConnection.disconnect();
				return Optional.empty();
			}
			
			if (rcode != 200) {
				logger().debug("failed: %d %s", rcode, httpConnection.getResponseMessage());
				httpConnection.disconnect();
				throw new IOException(String.format("unable to query http: %d %s : %s", rcode, httpConnection.getResponseMessage(), url.toString()));
			}
		}
		
		try {
			
			InputStream stream = connection.getInputStream();
			
			if (cache != null) {
				try {
					if (!cache.getParentFile().isDirectory() && !cache.getParentFile().mkdirs())
						throw new IOException("failed to create cache directory: %s" + cache.getParentFile());
					OutputStream cstream = new FileOutputStream(cache);
					cstream.write(stream.readAllBytes());
					cstream.close();
					stream = new FileInputStream(cache);
				} catch (IOException e) {
					throw new IOException("failed to write/read cache file: " + cache, e);
				}
			}

			return Optional.of(stream);
			
		} catch (FileNotFoundException e) {
			return Optional.empty();
		}
		
	}
	
	@FunctionalInterface
	protected static interface ExceptionFunction<P, R, E extends Throwable> {
		public R apply(P param) throws E;
	}
	
	protected <T> Optional<T> verifyData(InputStream stream, SkipOptional<InputStream> md5, SkipOptional<InputStream> sha1, SkipOptional<InputStream> sha256, SkipOptional<InputStream> sha512, ExceptionFunction<InputStream, T, Exception> parser) throws IOException {
		
		byte[] data = stream.readAllBytes();
		
		do {
			try {
				if (sha512.isPresent()) {
					MessageDigest alg = MessageDigest.getInstance("SHA512");
					String hash = new String(sha512.get().readAllBytes()).toLowerCase();
					ByteBuffer buf = ByteBuffer.wrap(alg.digest(data));
					String dataHash = Stream.generate(buf::get).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
					
					if (!hash.equals(dataHash)) return Optional.empty();
					break;
				}
			} catch (NoSuchAlgorithmException e) {}
	
			try {
				if (sha256.isPresent()) {
					MessageDigest alg = MessageDigest.getInstance("SHA256");
					String hash = new String(sha256.get().readAllBytes()).toLowerCase();
					ByteBuffer buf = ByteBuffer.wrap(alg.digest(data));
					String dataHash = Stream.generate(buf::get).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
					
					if (!hash.equals(dataHash)) return Optional.empty();
					break;
				}
			} catch (NoSuchAlgorithmException e) {}
	
			try {
				if (sha1.isPresent()) {
					MessageDigest alg = MessageDigest.getInstance("SHA1");
					String hash = new String(sha1.get().readAllBytes()).toLowerCase();
					ByteBuffer buf = ByteBuffer.wrap(alg.digest(data));
					String dataHash = Stream.generate(buf::get).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
					
					if (!hash.equals(dataHash)) return Optional.empty();
					break;
				}
			} catch (NoSuchAlgorithmException e) {}
	
			try {
				if (md5.isPresent()) {
					MessageDigest alg = MessageDigest.getInstance("MD5");
					String hash = new String(md5.get().readAllBytes()).toLowerCase();
					ByteBuffer buf = ByteBuffer.wrap(alg.digest(data));
					String dataHash = Stream.generate(buf::get).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
					
					if (!hash.equals(dataHash)) return Optional.empty();
					break;
				}
			} catch (NoSuchAlgorithmException e) {}
			
		} while (false);
		
		try {
			return Optional.ofNullable(parser.apply(new ByteArrayInputStream(data)));
		} catch (Exception e) {
			throw new IOException("invalid data!", e);
		}
		
	}

	protected static Node getNodeOpt(Node parent, String name) {
		for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
			if (parent.getChildNodes().item(i).getNodeName().equals(name)) return parent.getChildNodes().item(i);
		}
		return null;
	}

	protected static Node getNode(Node parent, String name) throws IOException {
		for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
			if (parent.getChildNodes().item(i).getNodeName().equals(name)) return parent.getChildNodes().item(i);
		}
		throw new IOException(String.format("no such node in XML: %s / %s", parent.getNodeName(), name));
	}

	protected static Stream<Node> getStream(Node parent) {
		return IntStream.range(0, parent.getChildNodes().getLength()).mapToObj(i -> parent.getChildNodes().item(i));
	}
	
	/**
	 * Attempts to query the repositories metadata file and verifies that the requested artifacts are available.
	 * @param repository The repository to query the artifacts from
	 * @param group The group id
	 * @param artifact The artifact id
	 * @param version The version
	 * @return true if the artifact is available on in the repositories metadata
	 */
	protected boolean checkVersions(MavenRepository repository, String group, String artifact, String version) {

		try {
			
			URL artifactMetaURL = repository.getArtifactURL(group, artifact, "maven-metadata.xml");
			if (artifactMetaURL.getProtocol().equals("file")) return true; // indicates maven local repository
			Optional<InputStream> artifactMeta = queryFile(artifactMetaURL, null, repository.credentials());
			if (artifactMeta.isEmpty()) return false;
			SkipOptional<InputStream> artifactMetaSHA512 = strictHashVerify ? SkipOptional.of(queryFile(repository.getArtifactURL(group, artifact, "maven-metadata.xml.sha512"), null, repository.credentials())) : SkipOptional.skipped();
			SkipOptional<InputStream> artifactMetaSHA256 = artifactMetaSHA512.isEmpty() ? SkipOptional.of(queryFile(repository.getArtifactURL(group, artifact, "maven-metadata.xml.sha256"), null, repository.credentials())) : SkipOptional.skipped();
			SkipOptional<InputStream> artifactMetaSHA1 = artifactMetaSHA256.isEmpty() ? SkipOptional.of(queryFile(repository.getArtifactURL(group, artifact, "maven-metadata.xml.sha1"), null, repository.credentials())) : SkipOptional.skipped();
			SkipOptional<InputStream> artifactMetaMD5 = artifactMetaSHA1.isEmpty() ? SkipOptional.of(queryFile(repository.getArtifactURL(group, artifact, "maven-metadata.xml.md5"), null, repository.credentials())) : SkipOptional.skipped();
			
			Optional<Document> artifactMetaDoc = verifyData(artifactMeta.get(), artifactMetaMD5, artifactMetaSHA1, artifactMetaSHA256, artifactMetaSHA512, this.builder::parse);
			if (artifactMetaDoc.isEmpty()) {
				logger().warn("invalid hash on repository artifact meta: [%s] %s:%s", repository.name(), group, artifact);
				return false;
			}
			
			Node metadata = getNode(artifactMetaDoc.get(), "metadata");
			String groupNode = getNode(metadata, "groupId").getFirstChild().getNodeValue();
			String artifactNode = getNode(metadata, "artifactId").getFirstChild().getNodeValue();
			if (!groupNode.equals(group) || !artifactNode.equals(artifact)) {
				logger().warn("repository artifact meta data reffers different groupID/artifactID: [%s] %s %s:%s -> %s:%s", repository.id, repository.url, group, artifact, groupNode, artifactNode);
				return false;
			}

			Node versioningNode = getNode(metadata, "versioning");
			boolean flag = false;
			
			if (version.equals("latest")) {
				version = getNode(versioningNode, "latest").getFirstChild().getNodeValue();
				flag = true;
			} else if (version.equals("release")) {
				version = getNode(versioningNode, "release").getFirstChild().getNodeValue();
				flag = true;
			}
			
			Node versionsNode = getNode(versioningNode, "versions");
			List<String> versions = getStream(versionsNode).filter(n -> n.getNodeType() == 1).map(n -> n.getFirstChild().getNodeValue()).toList();

			String fv = version;
			if (versions.stream().filter(v -> v.equals(fv)).count() == 0) {
				logger().debug("version not found in repository: [%s] %s", repository.id, version);
				if (flag) logger().warn("repository artifact meta info reffers to invalid latest/release version: [%s] %s : %s", repository.id, repository.url, version);
				if (strictMavenMeta) {
					return false;
				} else {
					logger.info("repositiory [%s] does not contain artifact version '%s' in maven-meta, try query anyway", repository.id, version);
				}
			}
			
			return true;
			
		} catch (Exception e) {
			
			logger().warn("failed to request or process meta data from repository: [%s] %s %s:%s", repository.id, repository.url, group, artifact, e);
			return false;
			
		}
		
	}
	
	/**
	 * Tries to resolve the artifacts on the provided repository.<br>
	 * If successfully, the artifact files will be placed in the cache and be locally available, the POM of the artifacts will be returned.
	 * @param repository The repository to look for the artifacts
	 * @param group The group id
	 * @param artifact The artifact id
	 * @param version The artifact version
	 * @param configurations 
	 * @return
	 */
	protected Optional<POM> tryResolve(MavenRepository repository, String group, String artifact, String version, String[] configurations) {
		
		if (!checkVersions(repository, group, artifact, version)) return Optional.empty();
		
		try {

			File localCache = new File(this.cache, group + "/" + artifact + "/" + version);
			String artifactName = artifact + "-" + version;
			
			Optional<InputStream> pomStream = queryFile(repository.getVersionURL(group, artifact, version, artifactName + ".pom"), new File(localCache, artifactName + ".pom"), repository.credentials);
			if (pomStream.isEmpty()) return Optional.empty(); // version definitely not available
			SkipOptional<InputStream> pomStreamSHA512 = strictHashVerify ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + ".pom.sha512"), null, repository.credentials)) : SkipOptional.skipped();
			SkipOptional<InputStream> pomStreamSHA256 = pomStreamSHA512.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + ".pom.sha256"), null, repository.credentials)) : SkipOptional.skipped();
			SkipOptional<InputStream> pomStreamSHA1 = pomStreamSHA256.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + ".pom.sha1"), null, repository.credentials)) : SkipOptional.skipped();
			SkipOptional<InputStream> pomStreamMD5 = pomStreamSHA1.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + ".pom.md5"), null, repository.credentials)) : SkipOptional.skipped();
			Optional<Document> pomDoc = verifyData(pomStream.get(), pomStreamMD5, pomStreamSHA1, pomStreamSHA256, pomStreamSHA512, this.builder::parse);
			
			Optional<POM> pom = parsePOM(pomDoc.get(), group, artifact, version);
			
			if (configurations != null) {
				
				for (String config : configurations) {
					String artifactFileName = config.isEmpty() ? artifactName + ".jar" : artifactName + "-" + config + ".jar";
					Optional<InputStream> jarStream = queryFile(repository.getVersionURL(group, artifact, version, artifactFileName), new File(localCache, artifactFileName), repository.credentials);
					if (jarStream.isPresent()) {
						SkipOptional<InputStream> jarStreamSHA512 = strictHashVerify ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + "-" + config + ".jar.sha512"), null, repository.credentials)) : SkipOptional.skipped();
						SkipOptional<InputStream> jarStreamSHA256 = jarStreamSHA512.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + "-" + config + ".jar.sha256"), null, repository.credentials)) : SkipOptional.skipped();
						SkipOptional<InputStream> jarStreamSHA1 = jarStreamSHA256.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + "-" + config + ".jar.sha1"), null, repository.credentials)) : SkipOptional.skipped();
						SkipOptional<InputStream> jarStreamMD5 = jarStreamSHA1.isEmpty() ? SkipOptional.of(queryFile(repository.getVersionURL(group, artifact, version, artifactName + "-" + config + ".jar.md5"), null, repository.credentials)) : SkipOptional.skipped();
						boolean result = verifyData(jarStream.get(), jarStreamMD5, jarStreamSHA1, jarStreamSHA256, jarStreamSHA512, s -> true).isPresent();
						
						if (!result) {
							logger().warn("invalid hash for artifact: [%s] %s:%s:%s '%s'", repository.name(), group, artifact, version, config);
							return Optional.empty();
						}
					}
				}
				
			}
			
			return pom;
			
		} catch (Exception e) {
			logger().warn("unexpected error while trying to request dependency: [%s] %s %s:%s:%s", repository.id, repository.url, group, artifact, version, e);
			return Optional.empty();
		}
		
	}

	public static record POM(ArtifactAbs source, List<ArtifactAbs> dependenciesAbs, List<Artifact> dependencies, List<ArtifactAbs> declerations, List<ArtifactAbs> imports, List<MavenRepository> repositorities) {
		public POM(ArtifactAbs source) {
			this(source, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		}
		
		public static enum Scope {
			IMPORT,
			COMPILE,
			PROVIDE,
			RUNTIME,
			TEST,
			SYSTEM;
		}
		public static record Artifact(Scope scope, String group, String artifact) { }
		public static record ArtifactAbs(Scope scope, String group, String artifact, String version) { }
		
		public void importPOM(ArtifactAbs importArtifact, POM other) {
			this.declerations.addAll(other.declerations);
			this.dependencies.addAll(other.dependencies);
			this.dependenciesAbs.addAll(other.dependenciesAbs);
			this.repositorities.addAll(other.repositorities());
			this.imports.remove(importArtifact);
		}
	}
	
	protected static final Pattern PROP_PATTERN = Pattern.compile("\\$\\{([^\\$\\{\\}]+)\\}");
	
	protected static String parseProps(Map<String, String> props, String text) {
		Matcher m = PROP_PATTERN.matcher(text);
		return m.replaceAll(r -> props.getOrDefault(r.group(1), "<undefined_prop>"));
	}
	
	protected Optional<POM> parsePOM(Document pom, String group, String artifact, String version) {
		
		try {
			
			POM pomObj = new POM(new POM.ArtifactAbs(Scope.COMPILE, group, artifact, version));
			
			Node project = getNode(pom, "project");
			
			// Parse properties
			HashMap<String, String> props = new HashMap<>();
			Node properties = getNodeOpt(project, "properties");
			if (properties != null) {
				for (Node propNode : getStream(properties).filter(n -> n.getFirstChild() != null).toList()) {
					String prop = propNode.getNodeName();
					String val = propNode.getFirstChild().getNodeValue();
					props.put(prop, val);
				}
			}
			
			// Parse parent POM import
			Node parent = getNodeOpt(project, "parent");
			if (parent != null) {
				String pgroup = parseProps(props, getNode(parent, "groupId").getFirstChild().getNodeValue());
				String partifact = parseProps(props, getNode(parent, "artifactId").getFirstChild().getNodeValue());
				String pversion = parseProps(props, getNode(parent, "version").getFirstChild().getNodeValue());
				pomObj.imports().add(new POM.ArtifactAbs(Scope.IMPORT, pgroup, partifact, pversion));
			}
			
			// Parse other imports
			Node dependencyManagement = getNodeOpt(project, "dependencyManagement");
			if (dependencyManagement != null) {
				Node dependencies = getNodeOpt(dependencyManagement, "dependencies");
				if (dependencies != null) {
					for (Node dependency : getStream(dependencies).filter(n -> n.getNodeName().equals("dependency")).toList()) {
						String dgroup = parseProps(props, getNode(dependency, "groupId").getFirstChild().getNodeValue());
						String dartifact = parseProps(props, getNode(dependency, "artifactId").getFirstChild().getNodeValue());
						String dversion = parseProps(props, getNode(dependency, "version").getFirstChild().getNodeValue());
						
						Node scope = getNodeOpt(dependency, "scope");
						Scope dscope = scope != null ? Scope.valueOf(scope.getFirstChild().getNodeValue().toUpperCase()) : Scope.COMPILE;
						Node type = getNodeOpt(dependency, "type");
						String dtype = type != null ? type.getFirstChild().getNodeValue() : "jar";
						
						if (dtype.equalsIgnoreCase("pom") && dscope == Scope.IMPORT) {
							pomObj.imports().add(new POM.ArtifactAbs(dscope, dgroup, dartifact, dversion));
						} else if (!dtype.equalsIgnoreCase("pom") && dscope != Scope.IMPORT) {
							pomObj.declerations().add(new POM.ArtifactAbs(dscope, dgroup, dartifact, dversion));
						} else {
							logger().warn("invalid dependency management configuration: %s:%s:%s scope %s type %s", dgroup, dartifact, dversion, dscope, dtype);;
						}
						
					}
				}
			}
			
			// Parse actual dependencies
			Node dependencies = getNodeOpt(project, "dependencies");
			if (dependencies != null) {
				for (Node dependency : getStream(dependencies).filter(n -> n.getNodeName().equals("dependency")).toList()) {
					String dgroup = parseProps(props, getNode(dependency, "groupId").getFirstChild().getNodeValue());
					String dartifact = parseProps(props, getNode(dependency, "artifactId").getFirstChild().getNodeValue());
					Node versionN = getNodeOpt(dependency, "version");

					Node scope = getNodeOpt(dependency, "scope");
					Scope dscope = scope != null ? Scope.valueOf(scope.getFirstChild().getNodeValue().toUpperCase()) : Scope.COMPILE;
					
					if (versionN != null) {
						pomObj.dependenciesAbs().add(new POM.ArtifactAbs(dscope, dgroup, dartifact, parseProps(props, versionN.getFirstChild().getNodeValue())));
					} else {
						pomObj.dependencies().add(new POM.Artifact(dscope, dgroup, dartifact));
					}
				}
			}
			
			// Parse additional repositories to search in
			Node repositorities = getNodeOpt(project, "repositorities");
			if (repositorities != null) {
				for (Node repositority : getStream(repositorities).filter(n -> n.getNodeName().equals("repositority")).toList()) {
					String name = parseProps(props, getNode(repositority, "name").getFirstChild().getNodeValue());
					String url = parseProps(props, getNode(repositority, "url").getFirstChild().getNodeValue());
					
					pomObj.repositorities().add(new MavenRepository(name, url, null));
				}
			}
			
			return Optional.of(pomObj);
			
		} catch (IOException e) {
			logger().warn("failed to parse POM! ", e);
			return Optional.empty();
		}
		
		
	}
	
}
