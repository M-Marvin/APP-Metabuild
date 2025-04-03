package de.m_marvin.metabuild.java.script;

import java.io.File;

import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.FileTask;
import de.m_marvin.metabuild.core.tasks.FileTask.Action;
import de.m_marvin.metabuild.java.JavaSourceIncludes;
import de.m_marvin.metabuild.java.tasks.JarTask;
import de.m_marvin.metabuild.java.tasks.JavaCompileTask;
import de.m_marvin.metabuild.java.tasks.JavaRunClasspathTask;
import de.m_marvin.metabuild.java.tasks.MavenDependTask;

public class JavaBuildScript extends BuildScript {
	
	public String projectName = "Project";
	
	public MavenDependTask implementation;
	public MavenDependTask runtime;
	public JavaCompileTask compileJava;
	public JarTask jar;
	public BuildTask build;
	
	@Override
	public void init() {
		
		implementation = new MavenDependTask("javaDependImpl");
		implementation.group = "depend";
		implementation.classpath = new File("build/implementation.classpath");
		
		runtime = new MavenDependTask("javaDependRun");
		runtime.group = "depend";
		runtime.classpath = new File("build/runtime.classpath");
		
		compileJava = new JavaCompileTask("compileJava");
		compileJava.group = "build";
		compileJava.sourcesDir = new File("src/main/java");
		compileJava.classesDir = new File("build/classes/main/java");
		compileJava.classpath.add(implementation.classpath);
		compileJava.dependsOn(implementation);
		
		jar = new JarTask("jar");
		jar.group = "build";
		jar.entries.put(compileJava.classesDir, "");
		jar.entries.put(new File("src/main/resource"), "");
		jar.archive = new File(String.format("build/libs/%s.jar", projectName));
		jar.dependsOn(compileJava);
		jar.dependsOn(runtime);
		
		repositories();
		dependencies();
		manifest();
		
		build = new BuildTask("build");
		build.group = "build";
		build.dependsOn(jar);
		
		new FileTask("clean", Action.DELETE, new File("build"));
		
	}
	
	@Override
	public void finish() {
		
		JavaSourceIncludes.include(implementation.getDependencyEntries());
		
	}
	
	public void repositories() {
		
	}
	
	public void dependencies() {

		runtime.extendsFrom(implementation);
		
	}
	
	public void packageDependencies() {
		
		jar.classpathIncludes.add(runtime.classpath);
		
	}
	
	public void makeRunnable() {

		var runJava = new JavaRunClasspathTask("run");
		runJava.group = "run";
		runJava.classpath = runtime.classpath;
		runJava.classesDir = compileJava.classesDir;
		runJava.mainClass = jar.metainfo.get("Main-Class");
		runJava.dependsOn(jar);
		runJava.dependsOn(runtime);
		
	}
	
	public void withSourcesJar() {

		var sourcesJar = new JarTask("sourcesJar");
		sourcesJar.group = "build";
		sourcesJar.entries.put(new File("src/main/java"), "");
		sourcesJar.archive = new File(String.format("build/libs/%s-sources.jar", projectName));
		sourcesJar.dependencyOf("build");
		
	}
	
	public void manifest() {
		
	}
	
}
