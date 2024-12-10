package de.m_marvin.metabuild.script;

import java.io.File;

import de.m_marvin.metabuild.tasks.BuildTask;
import de.m_marvin.metabuild.tasks.java.JarTask;
import de.m_marvin.metabuild.tasks.java.JavaCompileTask;

// TODO only for testing
public class JavaBuildScript extends BuildScript {
	
	public String projectName = "Project";
	
	@Override
	public void init() {
		
		var compileJava = new JavaCompileTask("compileJava");
		compileJava.sourcesDir = new File("src/main/java");
		compileJava.classesDir = new File("build/classes/main/java");
		
		var jar = new JarTask("jar");
		jar.entries.put(new File("build/classes/main/java/"), "");
		jar.entries.put(new File("src/main/resource"), "");
		jar.archive = new File(String.format("build/libs/%s.jar", this.projectName));
		jar.dependsOn(compileJava);
		
		var build = new BuildTask("build");
		build.dependsOn(jar);
		
		manifest(jar);
		
	}
	
	public void repositories() {
		
		
		
	}
	
	public void dependencies() {
		
		
		
	}
	
	public void withSourcesJar() {

		var sourcesJar = new JarTask("sourcesJar");
		sourcesJar.entries.put(new File("src/main/java"), "");
		sourcesJar.archive = new File(String.format("build/libs/%s-sources.jar", this.projectName));
		sourcesJar.dependencyOf("build");
		
	}
	
	public void manifest(JarTask jar) {
		
	}
	
}
