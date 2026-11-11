package de.m_marvin.mavenserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.m_marvin.basicxml.XmlException;
import de.m_marvin.basicxml.XmlReader;
import de.m_marvin.basicxml.XmlWriter;
import de.m_marvin.basicxml.dom.XmlDOM;
import de.m_marvin.basicxml.dom.XmlList;
import de.m_marvin.basicxml.dom.XmlNamed;
import de.m_marvin.basicxml.dom.XmlTag;
import de.m_marvin.mavenserver.types.ArtifactLocation;
import de.m_marvin.mavenserver.types.MavenDatabaseException;
import de.m_marvin.simplelogging.Log;

public class MavenDatabase {
	
	private final File rootLocation;
	private final int snapshotTimeToLive;
	
	public MavenDatabase(File rootLocation, int snapshotTimeToLive) throws IOException {
		this.rootLocation = rootLocation;
		this.snapshotTimeToLive = snapshotTimeToLive;
		
		if (!this.rootLocation.isDirectory() && !this.rootLocation.mkdir())
			throw new IOException("unable to create database directory");
	}
	
	public void checkPermission(File file) {
		if (!file.toPath().normalize().startsWith(this.rootLocation.toPath()))
			throw new IllegalArgumentException("unauthorized access to file system: " + file.toString());
	}
	
	public File getRootLocation() {
		return rootLocation;
	}
	
	public Stream<String> listGroupIds() {
		return Stream.of(this.rootLocation.listFiles())
			.filter(File::isDirectory)
			.map(File::getName);
	}
	
	public Stream<String> listArtifacts(String group) {
		return Stream.of(new File(this.rootLocation, group).listFiles())
			.filter(File::isDirectory)
			.map(File::getName);
	}
	
	public Stream<String> listVersions(String group, String artifact) {
		return Stream.of(new File(this.rootLocation, group + "/" + artifact).listFiles())
			.filter(File::isDirectory)
			.map(File::getName);
	}
	
	public synchronized OutputStream uploadArtifact(ArtifactLocation artifact) throws MavenDatabaseException {
		File uploadLocation = new File(this.rootLocation, artifact.getLocation());
		if (!uploadLocation.getParentFile().isDirectory() && !uploadLocation.getParentFile().mkdirs())
			throw new MavenDatabaseException("unable to create artifact directory: " + artifact);
		try {
			checkPermission(uploadLocation);
			return new FileOutputStream(uploadLocation);
		} catch (IOException e) {
			throw new MavenDatabaseException("unable to create artifact file: " + artifact, e);
		}
	}

	public synchronized InputStream downloadArtifact(ArtifactLocation artifact) throws MavenDatabaseException {
		File downloadLocation = new File(this.rootLocation, artifact.getLocation());
		if (!downloadLocation.isFile())
			return null;
		try {
			checkPermission(downloadLocation);
			return new FileInputStream(downloadLocation);
		} catch (IOException e) {
			throw new MavenDatabaseException("unable to access artifact file: " + artifact, e);
		}
	}
	
	public static final Pattern METADATA_PATTERN = Pattern.compile("maven-metadata.xml[\\w\\.]*");
	public static final int MAX_METADATA_HASH_LEN = 256;
	
	private static final XmlDOM DOM_PARSER = new XmlDOM();
	
	private final Map<String, String> metadataPath2groupMap = new HashMap<String, String>();
	private final Map<String, byte[]> pendingMetadataHashes = new HashMap<String, byte[]>();
	
	public synchronized void uploadMetadata(String path, InputStream data) throws MavenDatabaseException, IOException {
		
		if (path.endsWith(".xml")) {
			try {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				data.transferTo(buffer);
				data.close();
				buffer.close();
				XmlNamed metadataDom = DOM_PARSER.read(new XmlReader(new ByteArrayInputStream(buffer.toByteArray())));
				
				String group = metadataDom.getValue().getAsTag().getEntry("groupId").getAsTag().getInnerElements().findAny().get().getValue().getAsPrimitive().getAsString();
				boolean isVersionMetadata = metadataDom.getValue().getAsTag().getEntry("version") != null;
				String groupPath = path.substring(group.length() + 1);
				
				if (groupPath.split("/").length != (isVersionMetadata ? 3 : 2))
					throw new MavenDatabaseException("invalid path for metadata: " + path);
				
				this.metadataPath2groupMap.put(path, group);
				uploadMetadata0(group, groupPath, buffer.toByteArray());
				
				Iterator<Entry<String, byte[]>> pendingIter = this.pendingMetadataHashes.entrySet().iterator();
				while (pendingIter.hasNext()) {
					var entry = pendingIter.next();
					if (entry.getKey().startsWith(path)) {
						uploadMetadata0(group, entry.getKey().substring(group.length() + 1), entry.getValue());
						pendingIter.remove();
					}
				}
			} catch (XmlException | UnsupportedOperationException | NoSuchElementException e) {
				throw new MavenDatabaseException("malformed metadata xml: " + path, e);
			}
		} else {
			byte[] hashData = data.readNBytes(MAX_METADATA_HASH_LEN);
			data.close();
			if (hashData.length == MAX_METADATA_HASH_LEN)
				throw new MavenDatabaseException("metadata hash file contents exceed limits: " + path + " len: >" + MAX_METADATA_HASH_LEN);
			
			Optional<String> metadataPath = this.metadataPath2groupMap.keySet().stream()
				.filter(p -> path.startsWith(p))
				.findFirst();
			
			if (metadataPath.isPresent()) {
				String group = this.metadataPath2groupMap.get(metadataPath.get());
				uploadMetadata0(group, path.substring(group.length()), hashData);
			} else {
				this.pendingMetadataHashes.put(path, hashData);
			}
			
		}
		
	}
	
	public InputStream downloadMetadata(String path) throws MavenDatabaseException, IOException {
		Optional<String> group = listGroupIds()
			.filter(g -> path.startsWith(g.replace('.', '/')))
			.findFirst();
		if (group.isEmpty())
			return null;
		String groupPath = path.substring(group.get().length() + 1);
		File downloadLocation = new File(this.rootLocation, group.get() + "/" + groupPath);
		if (!downloadLocation.isFile())
			return null;
		try {
			checkPermission(downloadLocation);
			return new FileInputStream(downloadLocation);
		} catch (IOException e) {
			throw new MavenDatabaseException("unable to access metadata file: " + path, e);
		}
	}
	
	private void uploadMetadata0(String group, String path, byte[] data) throws MavenDatabaseException {
		File uploadLocation = new File(this.rootLocation, group + "/" + path);
		if (!uploadLocation.getParentFile().isDirectory() && !uploadLocation.getParentFile().mkdirs())
			throw new MavenDatabaseException("unable to create metadata directory: " + group + "/" + path);
		try {
			checkPermission(uploadLocation);
			OutputStream dataStream = new FileOutputStream(uploadLocation);
			dataStream.write(data);
			dataStream.close();
		} catch (IOException e) {
			throw new MavenDatabaseException("unable to create metadata file: " + group + "/" + path, e);
		}
	}
	
	public synchronized void cleanDatabase() {
		
		if (this.snapshotTimeToLive > 0)
			listGroupIds().forEach(this::cleanGroup);
		
	}
	
	public synchronized boolean cleanGroup(String group) {
		
		boolean noArtifacts = listArtifacts(group).filter(artifact -> !cleanArtifact(group, artifact)).count() == 0;

		if (noArtifacts) {
			File groupFile = new File(this.rootLocation, group);
			checkPermission(groupFile);
			groupFile.delete();

			Log.defaultLogger().infot("database/clean", "deleted empty group: %s", group);
			
			return true;
		}
		
		return false;
		
	}
	
	public synchronized boolean cleanArtifact(String group, String artifact) {
		
		boolean noVersions = listVersions(group, artifact).filter(version -> !cleanVersion(group, artifact, version)).count() == 0;
		
		if (noVersions) {
			File artifactFile = new File(this.rootLocation, group + "/" + artifact);
			checkPermission(artifactFile);
			Stream.of(artifactFile.listFiles())
				.filter(f -> f.getName().startsWith("maven-metadata"))
				.forEach(File::delete);
			artifactFile.delete();

			Log.defaultLogger().infot("database/clean", "deleted empty artifact: %s:%s", group, artifact);
			
			return true;
		}
		
		return false;
		
	}
	
	private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(?<year>\\d+)(?<month>\\d\\d)(?<day>\\d\\d)-(?<hour>\\d\\d)(?<minute>\\d\\d)(?<second>\\d\\d)");
	
	public synchronized boolean cleanVersion(String group, String artifact, String version) {
		if (!version.endsWith("-SNAPSHOT"))
			return false;
 
		File versionFile = new File(this.rootLocation, group + "/" + artifact + "/" + version);
		List<String> currentArtifacts = new ArrayList<String>();
	 	
		try {
			File metadataFile = new File(versionFile, "maven-metadata.xml");
			checkPermission(metadataFile);
			XmlNamed metaxml = DOM_PARSER.read(new XmlReader(new FileInputStream(metadataFile)));
			XmlTag versioning = metaxml.getValue().getAsTag().getEntry("versioning").getAsTag();
			XmlTag snapshotVersions = versioning.getEntry("snapshotVersions").getAsTag();
			XmlList snapshotVersionList = snapshotVersions.getEntryList("snapshotVersion");
			
			Instant now = Instant.now();
			Iterator<XmlTag> iter = snapshotVersionList.list().iterator();
			while (iter.hasNext()) {
				XmlTag snapshotVersion = iter.next();
				try {
					String timestampStr = snapshotVersion.getEntry("updated").getAsTag().getInnerText();
					Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(timestampStr);
					if (timestampMatcher.find()) {
						Instant timestamp = LocalDateTime.of(
								Integer.parseInt(timestampMatcher.group("year")),
								Integer.parseInt(timestampMatcher.group("month")),
								Integer.parseInt(timestampMatcher.group("day")),
								Integer.parseInt(timestampMatcher.group("hour")),
								Integer.parseInt(timestampMatcher.group("minute")),
								Integer.parseInt(timestampMatcher.group("second"))
							).toInstant(ZoneOffset.UTC);
						long age = Duration.between(timestamp, now).getSeconds();
						
						if (age > this.snapshotTimeToLive) {
							iter.remove();
						} else {
							String artifactName = artifact + "-" + snapshotVersion.getEntry("value").getAsTag().getInnerText();
							if (snapshotVersion.getEntry("classifier") != null)
								artifactName += "-" + snapshotVersion.getEntry("classifier").getAsTag().getInnerText();
							if (snapshotVersion.getEntry("extension") != null)
								artifactName += "." + snapshotVersion.getEntry("extension").getAsTag().getInnerText();
							currentArtifacts.add(artifactName);
							currentArtifacts.add(artifactName + ".md5");
							currentArtifacts.add(artifactName + ".sha1");
							currentArtifacts.add(artifactName + ".sha256");
							currentArtifacts.add(artifactName + ".sha512");
						}
					}
				} catch (UnsupportedOperationException | IndexOutOfBoundsException | NullPointerException e) {
					// malformed entry
					iter.remove();
				}
			}
			snapshotVersions.removeEntries("snapshotVersion");
			snapshotVersions.addEntry("snapshotVersion", snapshotVersionList);
			
			DOM_PARSER.write(new XmlWriter(new FileOutputStream(metadataFile)), metaxml);
			
		} catch (IOException | XmlException | NullPointerException e) {
			Log.defaultLogger().warnt("database/clean", "malformed snapshot metadata: %s:%s/%s", group, artifact, version);
		}

		checkPermission(versionFile);
		long deleted = Stream.of(versionFile.listFiles())
			.filter(File::isFile)
			.filter(f -> !f.getName().startsWith("maven-metadata"))
			.filter(f -> !currentArtifacts.contains(f.getName()))
			// TODO path check
			.filter(File::delete)
			.count();
		
		if (deleted > 0)
			Log.defaultLogger().infot("database/clean", "deleted outdated snapshots: %s:%s/%s (x%d)", group, artifact, version, deleted);
		
		if (currentArtifacts.isEmpty()) {
			Stream.of(versionFile.listFiles())
				.filter(f -> f.getName().startsWith("maven-metadata"))
				.forEach(File::delete);
			versionFile.delete();
	
			Log.defaultLogger().infot("database/clean", "deleted empty snapshot: %s:%s/%s", group, artifact, version);
			
			return true;
		}
		
		return false;
		
	}
	
}
