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
import de.m_marvin.metabuild.maven.tasks.MavenPublishTask;
import de.m_marvin.metabuild.maven.tasks.MavenResolveTask;

public class JavaBuildScript extends BuildScript {
	
	public String projectName = "Project";
	
	public MavenResolveTask dependencies;
	public MavenPublishTask publishMaven;
	public JavaCompileTask compileJava;
	public JarTask jar;
	public JarTask sourcesJar;
	public BuildTask build;
	
	@Override
	public void init() {
		
		super.init();
		
		dependencies = new MavenResolveTask("javaDependencies");
		dependencies.group = "dependencies";
		dependencies.fpCompiletime = new File("build/compile.classpath");
		dependencies.fpRunttime = new File("build/runtime.classpath");
		dependencies.fpTestCompiletime = new File("build/testcompile.classpath");
		dependencies.fpTestRuntime = new File("build/testruntime.classpath");
		
		compileJava = new JavaCompileTask("compileJava");
		compileJava.group = "build";
		compileJava.sourcesDir = new File("src/main/java");
		compileJava.classesDir = new File("build/classes/main/java");
		compileJava.classpath.add(dependencies.fpCompiletime);
		compileJava.dependsOn(dependencies);
		
		jar = new JarTask("jar");
		jar.group = "build";
		jar.entries.put(compileJava.classesDir, "");
		jar.entries.put(new File("src/main/resources"), "");
		jar.archive = new File(String.format("build/libs/%s.jar", projectName));
		jar.dependsOn(compileJava);
		
		repositories();
		dependencies();
		manifest();
		
		build = new BuildTask("build");
		build.group = "build";
		build.dependsOn(jar);

		publishMaven = new MavenPublishTask("publishMaven");
		publishMaven.group = "publish";
		publishMaven.dependsOn(jar);
		
		publishing();
		
		new FileTask("clean", Action.DELETE, new File("build"));
		
	}
	
	@Override
	public void finish() {
		
		JavaSourceIncludes.include(dependencies.getDependencyEntries());
		
	}
	
	public void repositories() {
		
	}
	
	public void dependencies() {
		
	}
	
	public void packageExecutable() {
		
		jar.includes.add(dependencies.fpRunttime);
		
	}
	
	public void makeRunnable() {

		var runJava = new JavaRunClasspathTask("run");
		runJava.group = "run";
		runJava.classpath.add(dependencies.fpRunttime);
		runJava.classesDir = compileJava.classesDir;
		runJava.mainClass = jar.metainfo.get("Main-Class");
		runJava.dependsOn(jar);
		runJava.dependsOn(dependencies);
		
	}
	
	public void withSourcesJar() {

		sourcesJar = new JarTask("sourcesJar");
		sourcesJar.group = "build";
		sourcesJar.entries.put(new File("src/main/java"), "");
		sourcesJar.archive = new File(String.format("build/libs/%s-sources.jar", projectName));
		sourcesJar.dependencyOf("build");
		
		publishMaven.artifacts.put("sources", sourcesJar.archive);
		publishMaven.dependsOn(sourcesJar);
		
	}
	
	public void manifest() {
		
	}
	
	public void publishing() {
		
		publishMaven.dependencies(this.dependencies);
		publishMaven.artifact("", jar.archive);
		
	}
	
}
