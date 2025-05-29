package de.m_marvin.metabuild.maven.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;

import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.handler.MavenResolver.ResolutionStrategy;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.Artifact.DataLevel;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveEntry;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveGroup;
import de.m_marvin.metabuild.maven.types.PublishConfiguration;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.ArtifactFile;
import de.m_marvin.metabuild.maven.types.Repository.Credentials;
import de.m_marvin.metabuild.maven.xml.ArtifactMetadata;
import de.m_marvin.metabuild.maven.xml.POM;
import de.m_marvin.metabuild.maven.xml.VersionMetadata;
import de.m_marvin.simplelogging.api.Logger;

public class MavenPublisher {

	protected final Logger logger;
	private TimeUnit remoteTimeoutUnit = TimeUnit.SECONDS;
	private long remoteTimeout = 5;
	private Consumer<String> statusCallback = s -> {};
	private final MavenResolver resolver;
	
	public MavenPublisher(Logger logger, MavenResolver resolver) {
		this.logger = logger;
		this.resolver = resolver;
	}
	
	public void setStatusCallback(Consumer<String> statusCallback) {
		this.statusCallback = statusCallback;
	}
	
	/**
	 * Sets the timeout for remote connections
	 */
	public void setRemoteTimeout(long timeout, TimeUnit unit) {
		this.remoteTimeout = timeout;
		this.remoteTimeoutUnit = unit;
		this.resolver.setRemoteTimeout(timeout, unit);
	}
	
	protected Logger logger() {
		return this.logger;
	}
	
	/**
	 * Attempts to upload the supplied publish configuration to all configured repositories.
	 * @param config The publish configuration, containing all relevant publish informations
	 * @param timeOfCreation The time to use for all timestamp's on the remote server
	 * @return true if the artifacts could be uploaded to ALL remote repositories, false even if only one of them failed
	 * @throws MavenException if an unexpected error occurred preventing from some of the uploads to even begin
	 */
	public boolean publishConfiguration(PublishConfiguration config) throws MavenException {
		Objects.requireNonNull(config, "publish config can not be null");
		Objects.requireNonNull(config.artifacts, "publish config artifacts can not be null");
		Objects.requireNonNull(config.coordinates, "publish config coordinates can not be null");
		Objects.requireNonNull(config.dependencies, "publish config dependencies can not be null");
		Objects.requireNonNull(config.repositories, "publish config repositories can not be null");
		Objects.requireNonNull(config.timeOfCreation, "publish config timeOfCreation can not be null");
		
		// force usage of remote files to prevent corruption trough cached files
		this.resolver.setResolutionStrategy(ResolutionStrategy.FORCE_REMOTE);
		
		// Verify all fields
		for (File f : config.artifacts.values()) {
			if (!f.isFile())
				throw new MavenException("file not found: %s", f);
		}
		
		// Fill POM structure
		POM pom = makePOM(config.coordinates, config.dependencies);
		
		// Attempt upload on all repositories
		boolean failure = false;
		for (Repository repository : config.repositories) {

			this.statusCallback.accept("upload artifacts > " + repository.name + " " + repository.baseURL);
			
			if (!uploadArtifacts(repository, config.artifacts, pom, config.timeOfCreation))
				failure = true;
			
		}
		
		return !failure;
		
	}

	/**
	 * Creates an POM instance for the supplied resolved dependency graph, listing all required transtive dependencies and repositories to find them.
	 * @param coordinates The artifact coordinates to which this POM file belongs to
	 * @param graph The resolved dependency graph for the artifacts
	 * @return the POM instance completely configured with all required variables
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public POM makePOM(Artifact coordinates, DependencyGraph graph) throws MavenException {
		
		POM pom = new POM();
		
		// Set POM coordinates
		pom.gavce(coordinates);
		
		// Set dependencies and repositories
		pom.dependencies = pom.new Dependencies();
		pom.repositories = pom.new Repositories();
		for (TransitiveGroup tg : graph.getTransitiveGroups()) {
			for (TransitiveEntry te : tg.artifacts) {
				
				// Add dependency
				POM.Dependency dependency = pom.new Dependency();
				dependency.gavce(te.artifact);
				dependency.scope = tg.scope;
				dependency.systemPath = te.systemPath;
				dependency.optional = te.optional;
				if (!tg.excludes.isEmpty()) {
					dependency.exclusions = dependency.new Exclusions();
					for (Artifact a : tg.excludes) {
						POM.Dependency.Exclusion exclusion = dependency.new Exclusion();
						exclusion.ga(a);
						dependency.exclusions.exclusion.add(exclusion);
					}
				}
				pom.dependencies.dependency.add(dependency);
				
				// Add repository
				if (tg.graph == null || tg.graph.getResolutionRepository() == null)
					throw new MavenException("publishing graph not resolved: %s", coordinates);
				Repository r = tg.graph.getResolutionRepository();
				if (!r.isLocal) { // local repositories (such as maven-local) are not written to the POM
					POM.Repository repository = pom.new Repository();
					repository.id = r.name.toLowerCase().replace(' ' , '-');
					repository.name = r.name;
					repository.url = r.baseURL.toString();
					if (!pom.repositories.repository.contains(repository))
						pom.repositories.repository.add(repository);
				}
				
			}
		}
		
		return pom;
		
	}
	
	protected static final String TIMESTAMP_FORMAT = "%04d%02d%02d-%02d%02d%02d";
	
	/**
	 * Attempts to upload all supplied artifacts to the remote repository server.
	 * @param repository The repository to upload the files to
	 * @param artifacts The artifacts to upload, represented by an configuration name to file location map
	 * @param pom The POM file for the artifacts
	 * @param timeOfCreation The time of creation to use inside all timestamps and metadata on the remote repository
	 * @return true if the files where all successfully uploaded and all metadata files updated
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public boolean uploadArtifacts(Repository repository, Map<String, File> artifacts, POM pom, ZonedDateTime timeOfCreation) throws MavenException {
		
		Artifact pomArtifact = pom.gavce().getPOMId();

		logger().info("attempt upload '%s' to repository: [%s] %s", pomArtifact, repository.name, repository.baseURL);

		// get timestamp
		String timestamp = String.format(TIMESTAMP_FORMAT, 
				timeOfCreation.getYear(), 
				timeOfCreation.getMonthValue(), 
				timeOfCreation.getDayOfMonth(), 
				timeOfCreation.getHour(), 
				timeOfCreation.getMinute(), 
				timeOfCreation.getSecond());
		
		if (pomArtifact.isSnapshot()) {
			
			// query meta information and calculate new build timestamp
			int buildNumber = 1;
			VersionMetadata versionMetadata = null;
			try {
				File snapshotMeta = this.resolver.downloadArtifact(repository, pomArtifact, DataLevel.META_VERSION);
				if (snapshotMeta != null) {
					versionMetadata = VersionMetadata.fromXML(new FileInputStream(snapshotMeta));
					buildNumber = versionMetadata.versioning.snapshot.buildNumber + 1;
				}
			} catch (FileNotFoundException | MavenException e) {
				throw new MavenException(e, "exception while requesting version level meta data for snapshot upload: " + pomArtifact);
			}
			if (versionMetadata == null)
				versionMetadata = new VersionMetadata();
			String buildTimestamp = timestamp + "-" + Integer.toString(buildNumber);
			
			// update version metadata
			versionMetadata.gav(pomArtifact);
			if (versionMetadata.versioning == null)
				versionMetadata.versioning = new VersionMetadata.Versioning();
			versionMetadata.versioning.lastUpdated = timestamp;
			versionMetadata.versioning.snapshot = new VersionMetadata.Versioning.Snapshot();
			versionMetadata.versioning.snapshot.buildNumber = buildNumber;
			versionMetadata.versioning.snapshot.timestamp = timestamp;
			if (versionMetadata.versioning.snapshotVersions == null)
				versionMetadata.versioning.snapshotVersions = new VersionMetadata.Versioning.SnapshotVersions();
			for (var cls : artifacts.entrySet()) {
				VersionMetadata.Versioning.SnapshotVersion versionData = new VersionMetadata.Versioning.SnapshotVersion();
				versionData.updated = timestamp;
				if (!cls.getKey().isBlank())
					versionData.classifier = cls.getKey();
				versionData.extension = FileUtility.getExtension(cls.getValue());
				versionData.value = pomArtifact.getNumericVersion() + "-" + buildTimestamp;
				versionMetadata.versioning.snapshotVersions.snapshotVersion.add(versionData);
			}
			VersionMetadata.Versioning.SnapshotVersion versionData = new VersionMetadata.Versioning.SnapshotVersion();
			versionData.updated = timestamp;
			versionData.extension = "pom";
			versionData.value = pomArtifact.getNumericVersion() + "-" + buildTimestamp;
			versionMetadata.versioning.snapshotVersions.snapshotVersion.add(versionData);
			
			// upload new version metadata
			try {
				uploadVersionMetadata(repository, pomArtifact, versionMetadata);
			} catch (MavenException e) {
				throw new MavenException(e, "unable to upload version metadata: %s", pomArtifact);
			}
			
			// delete local metadata file since it is now outdated
			File localMeta = new File(this.resolver.getLocalCache(), repository.getCacheFolder() +"/" + pomArtifact.getLocalPath(DataLevel.META_VERSION));
			if (localMeta.isFile()) localMeta.delete();
			
			pomArtifact = pomArtifact.withSnapshotVersion(buildTimestamp);
			
		}
		
		try {
			uploadArtifactPOM(repository, pomArtifact, pom);
		} catch (MavenException e) {
			throw new MavenException(e, "unable to upload pom: %s", pomArtifact);
		}
		
		for (String classifier : artifacts.keySet()) {
			File artifactFile = artifacts.get(classifier);
			String extenstion = FileUtility.getExtension(artifactFile);
			Artifact clsArtifact = pomArtifact.withClassifier(classifier, extenstion);
			
			try {
				uploadArtifact(repository, clsArtifact, DataLevel.ARTIFACT, new FileInputStream(artifactFile));
			} catch (MavenException e) {
				throw new MavenException(e, "unable to upload artifact: %s", artifactFile);
			} catch (IOException e) {
				throw new MavenException(e, "unable to open artifact for upload: %s", artifactFile);
			}
			
		}
		
		// download artifact metadata
		ArtifactMetadata artifactMetadata = null;
		try {
			File artifactMeta = this.resolver.downloadArtifact(repository, pomArtifact, DataLevel.META_ARTIFACT);
			if (artifactMeta != null) {
				artifactMetadata = ArtifactMetadata.fromXML(new FileInputStream(artifactMeta));
			}
		} catch (IOException e) {
			throw new MavenException(e, "unable to load artifact metadata: %s", pomArtifact.getGAV());
		}
		if (artifactMetadata == null)
			artifactMetadata = new ArtifactMetadata();
		
		// update artifact metadata
		artifactMetadata.ga(pomArtifact);
		if (artifactMetadata.versioning == null)
			artifactMetadata.versioning = new ArtifactMetadata.Versioning();
		artifactMetadata.versioning.lastUpdated = timestamp;
		artifactMetadata.versioning.latest = pomArtifact.baseVersion;
		if (!pomArtifact.isSnapshot())
			artifactMetadata.versioning.release = pomArtifact.baseVersion; 
		if (artifactMetadata.versioning.versions == null)
			artifactMetadata.versioning.versions = new ArtifactMetadata.Versioning.Versions();
		if (!artifactMetadata.versioning.versions.version.contains(pomArtifact.baseVersion))
			artifactMetadata.versioning.versions.version.add(pomArtifact.baseVersion);

		// upload new version metadata
		try {
			uploadArtifactMetadata(repository, pomArtifact, artifactMetadata);
		} catch (MavenException e) {
			throw new MavenException(e, "unable to upload artifact metadata: %s", pomArtifact);
		}
		
		return true;
		
	}

	/**
	 * Uploads an artifact level metadata file to an remote repository server.
	 * @param repository The remote repository server
	 * @param artifact The artifact coordinates to which the matadata belongs
	 * @param metadata The artifact level metadata to upload to the remote repository
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public void uploadArtifactMetadata(Repository repository, Artifact artifact, ArtifactMetadata metadata) throws MavenException {

		ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
		ArtifactMetadata.toXML(metadata, bufOut);
		ByteArrayInputStream bufIn = new ByteArrayInputStream(bufOut.toByteArray());
		
		uploadArtifact(repository, artifact, DataLevel.META_ARTIFACT, bufIn);
		
	}

	/**
	 * Uploads an version level metadata file to an remote repository server.
	 * @param repository The remote repository server
	 * @param artifact The artifact coordinates to which the matadata belongs
	 * @param metadata The version level metadata to upload to the remote repository
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public void uploadVersionMetadata(Repository repository, Artifact artifact, VersionMetadata metadata) throws MavenException {
		
		ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
		VersionMetadata.toXML(metadata, bufOut);
		ByteArrayInputStream bufIn = new ByteArrayInputStream(bufOut.toByteArray());
		
		uploadArtifact(repository, artifact, DataLevel.META_VERSION, bufIn);
		
	}

	/**
	 * Uploads an POM artifact file to an remote repository server.
	 * @param repository The remote repository server
	 * @param artifact The artifact coordinates
	 * @param pom The POM to upload to the remote repository
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public void uploadArtifactPOM(Repository repository, Artifact artifact, POM pom) throws MavenException {
		
		ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
		POM.toXML(pom, bufOut);
		ByteArrayInputStream bufIn = new ByteArrayInputStream(bufOut.toByteArray());
		
		uploadArtifact(repository, artifact, DataLevel.ARTIFACT, bufIn);
		
	}
	
	/**
	 * Uploads an artifact file to an remote repository server.
	 * @param repository The remote repository server
	 * @param artifact The artifact coordinates
	 * @param dataLevel The data level of the artifact related file (artifact itself or maven metedata level)
	 * @param stream The data stream to upload to the remote repository
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public void uploadArtifact(Repository repository, Artifact artifact, DataLevel dataLevel, InputStream stream) throws MavenException {
		
		// get available checksum algorithms
		Map<ArtifactFile, MessageDigest> hashes = new HashMap<>();
		for (ArtifactFile alg : ArtifactFile.checksums()) {
			try {
				hashes.put(alg, MessageDigest.getInstance(alg.getAlgorithm()));
			} catch (NoSuchAlgorithmException e) {
				logger().warn("upload checksum not available: %s", alg.getAlgorithm());
			}
		}
		
		try {
			// open remote connection
			URL remoteURL = repository.artifactURL(artifact, dataLevel, ArtifactFile.DATA);
			URLConnection connection = openURLConnection(remoteURL, repository.credentials);
			OutputStream onlineStream;
			if (connection.getURL().getProtocol().equals("file")) {
				File f = new File(connection.getURL().getPath());
				f.getParentFile().mkdirs();
				onlineStream = new FileOutputStream(f);
			} else {
				onlineStream = connection.getOutputStream();
			}
			
			// transfer bytes to remote repository, compute hash for checksums
			byte[] buffer = new byte[1024];
			int len = 0;
			while ((len = stream.read(buffer)) > 0) {
				int l = len;
				hashes.values().forEach(md -> md.update(buffer, 0, l));
				onlineStream.write(buffer, 0, len);
			}
			onlineStream.close();
			stream.close();
			
			// close connection
			if (!closeURLConnection(connection)) {
				throw new MavenException("unable to upload artifact to remote repository: %s %s", artifact, repository);
			}
			
			// upload checksums
			for (ArtifactFile checksum : hashes.keySet()) {
				String checksumStr = HexFormat.of().formatHex(hashes.get(checksum).digest());
				
				try {
					// open remote connection
					URL remoteChecksumURL = repository.artifactURL(artifact, dataLevel, checksum);
					URLConnection checksumConnection = openURLConnection(remoteChecksumURL, repository.credentials);
					OutputStream onlineChecksumStream;
					if (connection.getURL().getProtocol().equals("file")) {
						File f = new File(checksumConnection.getURL().getPath());
						f.getParentFile().mkdirs();
						onlineChecksumStream = new FileOutputStream(f);
					} else {
						onlineChecksumStream = checksumConnection.getOutputStream();
					}
					
					// upload checksum string
					onlineChecksumStream.write(checksumStr.getBytes());
					onlineChecksumStream.close();
					
					// close connection
					if (!closeURLConnection(checksumConnection)) {
						throw new MavenException("unable to upload artifact to remote repository: %s %s", artifact, repository);
					}
					
				} catch (IOException e) {
					throw new MavenException(e, "exception during upload of checksum to remote repository: %s %s", artifact, repository);
				}
			}
			
		} catch (IOException e) {
			throw new MavenException(e, "exception during upload to remote repository: %s %s", artifact, repository);
		}
		
	}
	
	/**
	 * Opens an URL connection to an remote server for uploading data
	 * @param url TThe remote URL to upload data to
	 * @param credentials The login credentials used to authenticate against the remote server
	 * @return the remote URL connection to upload data trough or null, if the server denied access or the path was invalid
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public URLConnection openURLConnection(URL url, Credentials credentials) throws MavenException {
		
		logger().debug("access URL: %s", url);
		
		try {
			
			// open remote connection and configure timeouts
			URLConnection connection = url.openConnection();
			connection.setReadTimeout((int) this.remoteTimeoutUnit.toMillis(this.remoteTimeout));
			connection.setDoOutput(true);
			if (connection instanceof HttpURLConnection httpConnection)
				httpConnection.setRequestMethod("PUT");
			
			// apply credentials if available
			if (credentials != null) {
				if (credentials.token() != null)
					connection.setRequestProperty("Authorization", "Bearer " + credentials.bearer());
				if (connection instanceof HttpsURLConnection httpsConnection && credentials.username() != null && credentials.password() != null)
					httpsConnection.setAuthenticator(credentials.authenticator());
			}
			
			// return connection stream
			return connection;
			
		} catch (FileNotFoundException e) {
			logger().debug("unable to get resource: %s", e.getMessage());
			return null;
		} catch (IOException e) {
			throw new MavenException(e, "exception while connecting to remote artifact: %s", url);
		}
		
	}
	
	/**
	 * Closes an upload URL connection, checking the response of the server for OK signals.
	 * @param connection The URL connection used for the upload
	 * @return true if the data was received and processed by the server successfully
	 * @throws MavenException if an unexpected error occurred which prevents further uploads of other artifacts
	 */
	public boolean closeURLConnection(URLConnection connection) throws MavenException {

		try {
			
			// if HTTP connection, check header status codes
			if (connection instanceof HttpURLConnection httpConnection) {
				int rcode = httpConnection.getResponseCode();
				
				if (rcode != 200) {
					logger().debug("not found: %d %s", rcode, httpConnection.getResponseMessage());
					return false;
				}
				httpConnection.disconnect();
			}
			
			return true;
			
		} catch (IOException e) {
			throw new MavenException(e, "exception while connecting to remote artifact: %s", connection.getURL());
		}
		
	}
	
}
