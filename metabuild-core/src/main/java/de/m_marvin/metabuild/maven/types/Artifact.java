package de.m_marvin.metabuild.maven.types;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.maven.exception.MavenException;

public class Artifact {
	
	public final String groupId;
	public final String artifactId;
	public final String baseVersion;
	public final String version;
	public final String classifier;
	public final String extension;
	
	private static final Pattern FIELD_PATTERN = Pattern.compile("[^: ]+");

	public Artifact(String groupId, String artifactId) throws MavenException {
		this(groupId, artifactId, null);
	}
	
	public Artifact(String groupId, String artifactId, String baseVersion) throws MavenException {
		this(groupId, artifactId, baseVersion, null);
	}

	public Artifact(String groupId, String artifactId, String baseVersion, String classifier) throws MavenException {
		this(groupId, artifactId, baseVersion, classifier, null);
	}

	public Artifact(String groupId, String artifactId, String baseVersion, String classifier, String extension) throws MavenException {
		this(groupId, artifactId, baseVersion, null, classifier, extension, true);
	}
	
	public Artifact(String groupId, String artifactId, String baseVersion, String version, String classifier, String extension) throws MavenException {
		this(groupId, artifactId, baseVersion, version, classifier, extension, true);
	}
	
	protected Artifact(String groupId, String artifactId, String baseVersion, String version, String classifier, String extension, boolean fillDefaults) throws MavenException {
		if (fillDefaults) {
			if (extension == null) extension = "jar";
			if (classifier == null) classifier = "";
		}
		if (groupId == null) throw new MavenException("illegal argument, groupId must not be null!");
		if (artifactId == null) throw new MavenException("illegal argument, artifactId must not be null!");
		if (!groupId.equals("*") && !FIELD_PATTERN.matcher(groupId).matches()) throw new MavenException("illegal argument, groupId must match '%s'", FIELD_PATTERN.toString());
		if (!artifactId.equals("*") && !FIELD_PATTERN.matcher(artifactId).matches()) throw new MavenException("illegal argument, artifactId must match '%s'", FIELD_PATTERN.toString());
		if (baseVersion != null && !FIELD_PATTERN.matcher(baseVersion).matches()) throw new MavenException("illegal argument, baseVersion must match '%s'", FIELD_PATTERN.toString());
		if (baseVersion == null && version != null) throw new MavenException("illegal argument, version set to non null while baseVersion set to null!");
		if (version != null && !FIELD_PATTERN.matcher(version).matches()) throw new MavenException("illegal argument, version must match '%s'", FIELD_PATTERN.toString());
		if (classifier != null && !FIELD_PATTERN.matcher(classifier).matches() && !classifier.isEmpty()) throw new MavenException("illegal argument, classifier must match '%s'", FIELD_PATTERN.toString());
		if (extension != null && !FIELD_PATTERN.matcher(extension).matches() && !extension.isEmpty()) throw new MavenException("illegal argument, extension must match '%s'", FIELD_PATTERN.toString());
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.baseVersion = baseVersion;
		this.classifier = classifier;
		this.extension = extension;
		this.version = isSnapshot() ? version : baseVersion;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Artifact other) {
			return	Objects.equals(this.groupId, other.groupId) &&
					Objects.equals(this.artifactId, other.artifactId) &&
					Objects.equals(this.baseVersion, other.baseVersion) &&
					Objects.equals(this.classifier, other.classifier) &&
					Objects.equals(this.extension, other.extension);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(this.groupId, this.artifactId, this.baseVersion, this.classifier, this.extension);
	}
	
	@Override
	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append(this.groupId).append(':').append(this.artifactId);
		if (this.baseVersion != null) {
			if (this.extension != null || this.classifier != null) {
				buff.append(':').append(this.classifier);
				if (this.extension != null) 
					buff.append(':').append(this.extension);
			}
			buff.append(':').append(this.baseVersion);
		}
		if (isSnapshot() && this.version != null) {
			buff.append(" (").append(this.version).append(')');
		}
		return buff.toString();
	}
	
	public static enum DataLevel {
		META_GROUP(true),
		META_ARTIFACT(true),
		META_VERSION(true),
		ARTIFACT(false);
		
		private final boolean isMetadata;
		
		private DataLevel(boolean isMetadata) {
			this.isMetadata = isMetadata;
		}
		
		public boolean isMetadata() {
			return isMetadata;
		}
	}
	
	public String getLocalPath(DataLevel level) {
		StringBuffer buff = new StringBuffer();
		buff.append('/').append(this.groupId.replace('.', '/'));
		if (level == DataLevel.META_GROUP) {
			buff.append('/').append("maven-matedata.xml");
			return buff.toString();
		} else {
			buff.append('/').append(this.artifactId);
			if (level == DataLevel.META_ARTIFACT) {
				buff.append('/').append("maven-matedata.xml");
				return buff.toString();
			} else {
				if (this.baseVersion != null) {
					buff.append('/').append(this.baseVersion);
					if (level == DataLevel.META_VERSION) {
						buff.append('/').append("maven-metadata.xml");
						return buff.toString();
					} else {
						buff.append('/').append(this.artifactId).append('-').append(this.version);
						if (this.classifier != null) {
							if (!this.classifier.isEmpty()) buff.append('-').append(this.classifier);
							if (this.extension != null) {
								buff.append('.').append(this.extension);
							}
						}
					}
				}
				return buff.toString();
			}
		}
	}
	
	private static final Pattern GAVCE_PATTERN = Pattern.compile("(?<group>[^: ]+):(?<artifact>[^: ]+)(:(?<classifier>[^: ]*)(:(?<extension>[^: ]+))?)?:(?<version>[^: ]+)");
	
	public static Artifact of(String gavce) throws MavenException {
		Matcher m = GAVCE_PATTERN.matcher(gavce);
		if (m.find()) {
			return new Artifact(
				m.group("group"),
				m.group("artifact"),
				m.group("version"),
				m.group("classifier"),
				m.group("extension")
			);
		}
		throw new MavenException("invalid coordinate synthax: '%s'", gavce);
	}
	
	public boolean isSnapshot() {
		return this.baseVersion != null && this.baseVersion.endsWith("-SNAPSHOT");
	}
	
	public boolean hasGAV() {
		return this.groupId != null && this.artifactId != null && this.baseVersion != null;
	}
	
	public boolean isGAWildcard() {
		return this.groupId.equals("*") || this.artifactId.equals("*");
	}
	
	public boolean hasGAVCE() {
		return this.groupId != null && this.artifactId != null && this.baseVersion != null && this.classifier != null && this.extension != null;
	}
	
	public Artifact getGAV() {
		try {
			return new Artifact(this.groupId, this.artifactId, this.baseVersion, null, null, null, false);
		} catch (MavenException e) { throw new RuntimeException("this makes not sense ..."); }
	}
	
	public Artifact getPOMId() throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get POM from wildcard: %s", this.toString());
		if (!hasGAV()) throw new MavenException("can not get POM from non GAV coordinates: %s", this.toString());
		return new Artifact(this.groupId, this.artifactId, this.baseVersion, this.version, "", "pom");
	}
	
	public Artifact withVersion(String version) throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get specific version from wildcard: %s", this.toString());
		return new Artifact(this.groupId, this.artifactId, version, null, this.classifier, this.extension, false);
	}
	
	public String getNumericVersion() throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get specific numeric version from wildcard: %s", this.toString());
		if (!hasGAV()) throw new MavenException("can not get specific numeric version from non GAV coordinates: %s", this.toString());
		if (isSnapshot()) {
			int i = this.baseVersion.lastIndexOf("-SNAPSHOT");
			return this.baseVersion.substring(0, i);
		}
		return this.baseVersion;
	}
	
	public Artifact withSnapshotVersion(String snapshotVersion) throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get specific snapshot version from wildcard: %s", this.toString());
		if (!isSnapshot()) throw new MavenException("can not get specific snapshot version from non snapshot: %s", this.toString());
		int i = this.baseVersion.lastIndexOf("SNAPSHOT");
		String version = this.baseVersion.substring(0, i) + snapshotVersion;
		return new Artifact(this.groupId, this.artifactId, this.baseVersion, version, this.classifier, this.extension, false);
	}
	
	public Artifact withClassifier(String classifier, String extension) throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get specific configuration from wildcard: %s", this.toString());
		if (!hasGAV()) throw new MavenException("can not get specific configuration from non GAV coordinates: %s", this.toString());
		return new Artifact(this.groupId, this.artifactId, this.baseVersion, this.version, classifier, extension, false);
	}
	
}
