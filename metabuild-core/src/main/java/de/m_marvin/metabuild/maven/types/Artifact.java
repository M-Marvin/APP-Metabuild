package de.m_marvin.metabuild.maven.types;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Artifact {
	
	public final String groupId;
	public final String artifactId;
	public final String version;
	public final String classifier;
	public final String extension;
	
	private static final Pattern FIELD_PATTERN = Pattern.compile("[^: ]+");

	public Artifact(String groupId, String artifactId) throws MavenException {
		this(groupId, artifactId, null);
	}
	
	public Artifact(String groupId, String artifactId, String version) throws MavenException {
		this(groupId, artifactId, version, null);
	}
	
	public Artifact(String groupId, String artifactId, String version, String classifier) throws MavenException {
		this(groupId, artifactId, version, classifier, null);
	}
	
	public Artifact(String groupId, String artifactId, String version, String classifier, String extension) throws MavenException {
		this(groupId, artifactId, version, classifier, extension, true);
	}
	
	protected Artifact(String groupId, String artifactId, String version, String classifier, String extension, boolean fillDefaults) throws MavenException {
		if (fillDefaults) {
			if (extension == null) extension = "jar";
			if (classifier == null) classifier = "";
		}
		if (groupId == null) throw new MavenException("illegal argument, groupId must not be null!");
		if (artifactId == null) throw new MavenException("illegal argument, artifactId must not be null!");
		
		
		
		if (!groupId.equals("*") && !FIELD_PATTERN.matcher(groupId).matches()) throw new MavenException("illegal argument, groupId must match '%s'", FIELD_PATTERN.toString());
		if (!artifactId.equals("*") && !FIELD_PATTERN.matcher(artifactId).matches()) throw new MavenException("illegal argument, artifactId must match '%s'", FIELD_PATTERN.toString());
		if (version != null && !FIELD_PATTERN.matcher(version).matches()) throw new MavenException("illegal argument, version must match '%s'", FIELD_PATTERN.toString());
		if (classifier != null && !FIELD_PATTERN.matcher(classifier).matches() && !classifier.isEmpty()) throw new MavenException("illegal argument, classifier must match '%s'", FIELD_PATTERN.toString());
		if (extension != null && !FIELD_PATTERN.matcher(extension).matches() && !extension.isEmpty()) throw new MavenException("illegal argument, extension must match '%s'", FIELD_PATTERN.toString());
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.classifier = classifier;
		this.extension = extension;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Artifact other) {
			return	Objects.equals(this.groupId, other.groupId) &&
					Objects.equals(this.artifactId, other.artifactId) &&
					Objects.equals(this.version, other.version) &&
					Objects.equals(this.classifier, other.classifier) &&
					Objects.equals(this.extension, other.extension);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(this.groupId, this.artifactId, this.version, this.classifier, this.extension);
	}
	
	@Override
	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append(this.groupId).append(':').append(this.artifactId);
		if (this.version != null) {
			if (this.extension != null || this.classifier != null) {
				buff.append(':').append(this.classifier);
				if (this.extension != null) 
					buff.append(':').append(this.extension);
			}
			buff.append(':').append(this.version);
		}
		return buff.toString();
	}
	
	public String getLocalPath() {
		StringBuffer buff = new StringBuffer();
		buff.append('/').append(this.groupId.replace('.', '/'));
		buff.append('/').append(this.artifactId);
		if (this.version != null) {
			buff.append('/').append(this.version).append('/').append(this.artifactId).append('-').append(this.version);
			if (this.classifier != null) {
				if (!this.classifier.isEmpty()) buff.append('-').append(this.classifier);
				if (this.extension != null) {
					buff.append('.').append(this.extension);
				}
			}
		}
		return buff.toString();
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
	
	// FIXME snapshot versions
	public boolean isSnapshot() {
		return this.version != null && this.version.endsWith("-SNAPSHOT");
	}
	
	public boolean hasGAV() {
		return this.groupId != null && this.artifactId != null && this.version != null;
	}
	
	public boolean isGAWildcard() {
		return this.groupId.equals("*") || this.artifactId.equals("*");
	}
	
	public boolean hasGAVCE() {
		return this.groupId != null && this.artifactId != null && this.version != null && this.classifier != null && this.extension != null;
	}
	
	public Artifact getGAV() {
		try {
			return new Artifact(this.groupId, this.artifactId, this.version, null, null, false);
		} catch (MavenException e) { throw new RuntimeException("this makes not sense ..."); }
	}
	
	public Artifact getPOMId() throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get POM from wildcard: %s", this.toString());
		if (!hasGAV()) throw new MavenException("can not get POM from non GAV coordinates: %s", this.toString());
		return new Artifact(this.groupId, this.artifactId, this.version, "", "pom");
	}
	
	public Artifact withVersion(String version) throws MavenException {
		if (isGAWildcard()) throw new MavenException("can not get specific version from wildcard: %s", this.toString());
		return new Artifact(this.groupId, this.artifactId, version, this.classifier, this.extension, false);
	}
	
}
