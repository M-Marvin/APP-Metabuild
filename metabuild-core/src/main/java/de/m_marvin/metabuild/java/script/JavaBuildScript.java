package de.m_marvin.metabuild.java.script;

import java.io.File;

import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.FileTask;
import de.m_marvin.metabuild.core.tasks.FileTask.Action;
import de.m_marvin.metabuild.java.devenv.JavaSourceIncludes;
import de.m_marvin.metabuild.java.tasks.JarTask;
import de.m_marvin.metabuild.java.tasks.JavaCompileTask;
import de.m_marvin.metabuild.java.tasks.JavaRunClasspathTask;
import de.m_marvin.metabuild.java.tasks.MavenDependTask;

public class JavaBuildScript extends BuildScript {
	
	public String projectName = "Project";
	
	public MavenDependTask implementation;
	public MavenDependTask runtime;
	
	public JavaSourceIncludes devenv;
	
	@Override
	public void init() {
		
		this.devenv = new JavaSourceIncludes();
		
		this.implementation = new MavenDependTask("javaDependImpl");
		this.implementation.group = "depend";
		this.implementation.classpath = new File("build/implementation.classpath");
		
		this.runtime = new MavenDependTask("javaDependRun");
		this.runtime.group = "depend";
		this.runtime.classpath = new File("build/runtime.classpath");
		
		repositories();
		dependencies();
		
		this.runtime.extendsFrom(this.implementation);
		
		var compileJava = new JavaCompileTask("compileJava");
		compileJava.group = "build";
		compileJava.sourcesDir = new File("src/main/java");
		compileJava.classesDir = new File("build/classes/main/java");
		compileJava.classpath = this.implementation.classpath;
		compileJava.dependsOn(this.implementation);
		
		var jar = new JarTask("jar");
		jar.group = "build";
		jar.entries.put(compileJava.classesDir, "");
		jar.entries.put(new File("src/main/resource"), "");
		jar.archive = new File(String.format("build/libs/%s.jar", this.projectName));
		jar.dependsOn(compileJava);
		
		var build = new BuildTask("build");
		build.group = "build";
		build.dependsOn(jar);
		
		manifest(jar);
		
		var runJava = new JavaRunClasspathTask("run");
		runJava.group = "run";
		runJava.classpath = this.runtime.classpath;
		runJava.classesDir = compileJava.classesDir;
		runJava.mainClass = jar.metainfo.get("Main-Class");
		runJava.dependsOn(jar);
		runJava.dependsOn(this.runtime);
		
		new FileTask("clean", Action.DELETE, new File("build"));
		
	}
	
	@Override
	public void finish() {
		
		this.devenv.sourceJars.addAll(this.implementation.getClasspathEntries());
		
	}
	
	public void repositories() {
		
	}
	
	public void dependencies() {
		
	}
	
	public void withSourcesJar() {

		var sourcesJar = new JarTask("sourcesJar");
		sourcesJar.group = "build";
		sourcesJar.entries.put(new File("src/main/java"), "");
		sourcesJar.archive = new File(String.format("build/libs/%s-sources.jar", this.projectName));
		sourcesJar.dependencyOf("build");
		
	}
	
	public void manifest(JarTask jar) {
		
	}
	
}
