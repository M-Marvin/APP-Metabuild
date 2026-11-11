package de.m_marvin.mavenserver.types;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArtifactLocation {
	
	public final String group;
	public final String artifact;
	public final String version;
	public final String file;
	
	public ArtifactLocation(String group, String artifact, String version, String file) {
		this.group = group;
		this.artifact = artifact;
		this.version = version;
		this.file = file;
		
		if (!this.file.startsWith(this.artifact))
			throw new IllegalArgumentException("file name does not start with artifact name");
	}
	
	private static final Pattern PATH_PATTERN = Pattern.compile("(?<group>[\\w\\.-\\/]+)\\/(?<artifact>[\\w\\.-]+)\\/(?<version>[\\w\\.-]+)\\/(?<file>[\\w\\.-]+)");
	
	public static ArtifactLocation fromPath(String path) {
		Matcher m = PATH_PATTERN.matcher(path);
		if (!m.find())
			throw new IllegalArgumentException("illegal artifact path: " + path);
		return new ArtifactLocation(
				m.group("group").replace('/', '.'),
				m.group("artifact"),
				m.group("version"),
				m.group("file")
		);
	}

	public String getLocation() {
		return this.group + "/" + this.artifact + "/" + this.version + "/" + this.file;
	}
	
	@Override
	public String toString() {
		return this.group + ":" + this.artifact + ":" + this.version + "/" + this.file;
	}
	
}
