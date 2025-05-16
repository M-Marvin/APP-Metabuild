package de.m_marvin.metabuild.cpp.script;

import java.io.File;

import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.tasks.CommandLineTask;
import de.m_marvin.metabuild.core.tasks.FileTask;
import de.m_marvin.metabuild.core.tasks.FileTask.Action;
import de.m_marvin.metabuild.cpp.CppSourceIncludes;
import de.m_marvin.metabuild.cpp.tasks.CppCompileTask;
import de.m_marvin.metabuild.cpp.tasks.CppLinkTask;

public class CppBuildScript extends BuildScript {
	
	public String projectName = "Project";
	
	public CppCompileTask compileCpp;
	public CppLinkTask linkCpp;
	
	@Override
	public void init() {
		
		this.compileCpp = new CppCompileTask("compileCpp");
		this.compileCpp.group = "build";
		this.compileCpp.sourcesDir = new File("src/cpp");
		this.compileCpp.objectsDir = new File("build/objects");
		
		compillation();
		
		this.linkCpp = new CppLinkTask("linkCpp");
		this.linkCpp.group = "build";
		this.linkCpp.objectsDir = this.compileCpp.objectsDir;
		this.linkCpp.outputFile = new File("build/out/" + this.projectName + ".exe");
		this.linkCpp.dependsOn(this.compileCpp);
		
		linkage();

		var runCpp = new CommandLineTask("runCpp");
		runCpp.executable = this.linkCpp.outputFile;
		runCpp.dependsOn(this.linkCpp);
		
		new FileTask("clean", Action.DELETE, new File("build"));
		
	}
	
	@Override
	public void finish() {
		
		CppSourceIncludes.include(this.compileCpp.allIncludes());
		
	}
	
	public void sharedLib() {
		
		this.linkCpp.arguments.add("-shared");
		this.linkCpp.outputFile = new File("build/out/lib" + this.projectName + ".dll");
		
	}
	
	public void compillation() {
		
	}
	
	public void linkage() {
		
	}
	
}
