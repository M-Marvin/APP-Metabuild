package de.m_marvin.metabuild.maven;

import de.m_marvin.metabuild.maven.types.Repository;

public class Maven {
	
	private Maven() {}
	
	public static final Repository MAVEN_LOCAL = new Repository("Maven Local", "file:///" + System.getProperty("user.home") + "/.m2/repository");
	public static Repository mavenLocal() {
		return MAVEN_LOCAL;
	}
	
	public static final Repository MAVEN_CENTRAL = new Repository("Maven Central", "https://repo.maven.apache.org/maven2");
	public static Repository mavenCentral() {
		return MAVEN_CENTRAL;
	}
	
}
