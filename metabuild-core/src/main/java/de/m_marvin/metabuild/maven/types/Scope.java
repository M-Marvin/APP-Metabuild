package de.m_marvin.metabuild.maven.types;

import de.m_marvin.metabuild.maven.xml.POM;

public enum Scope {
	
	COMPILE(POM.Dependency.Scope.COMPILE),
	PROVIDED(POM.Dependency.Scope.COMPILE),
	RUNTIME(POM.Dependency.Scope.COMPILE),
	TEST(POM.Dependency.Scope.COMPILE),
	SYSTEM(POM.Dependency.Scope.COMPILE);
	
	private final POM.Dependency.Scope mavenScope;
	
	private Scope(POM.Dependency.Scope mavenScope) {
		this.mavenScope = mavenScope;
	}
	
	public POM.Dependency.Scope mavenScope() {
		return this.mavenScope;
	}
	
	public static Scope fromMaven(POM.Dependency.Scope mavenScope) {
		for (Scope s : values())
			if (s.mavenScope == mavenScope) return s;
		throw new IllegalArgumentException("not a valid maven scope: " + mavenScope);
	}
	
}
