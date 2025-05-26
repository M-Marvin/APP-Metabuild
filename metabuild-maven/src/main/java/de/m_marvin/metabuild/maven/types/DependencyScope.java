package de.m_marvin.metabuild.maven.types;

import java.util.function.Predicate;

import de.m_marvin.metabuild.maven.xml.POM.Dependency.Scope;

public enum DependencyScope {
	
	COMPILETIME(scope -> scope == Scope.COMPILE || scope == Scope.PROVIDED || scope == Scope.SYSTEM),
	RUNTIME(scope -> scope == Scope.COMPILE || scope == Scope.SYSTEM || scope == Scope.RUNTIME),
	TEST_COMPILETIME(scope -> scope == Scope.COMPILE || scope == Scope.PROVIDED || scope == Scope.SYSTEM || scope == Scope.TEST),
	TEST_RUNTIME(scope -> scope == Scope.COMPILE || scope == Scope.SYSTEM || scope == Scope.RUNTIME || scope == Scope.TEST);
	
	private Predicate<Scope> includes;
	
	private DependencyScope(Predicate<Scope> includes) {
		this.includes = includes;
	}
	
	public boolean includes(Scope scope) {
		return this.includes.test(scope);
	}
	
}
