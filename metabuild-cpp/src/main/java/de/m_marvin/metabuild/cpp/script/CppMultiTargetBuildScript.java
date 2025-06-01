package de.m_marvin.metabuild.cpp.script;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.FileTask;
import de.m_marvin.metabuild.core.tasks.ZipTask;
import de.m_marvin.metabuild.core.tasks.FileTask.Action;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.cpp.tasks.CppCompileTask;
import de.m_marvin.metabuild.cpp.tasks.CppLinkTask;
import de.m_marvin.metabuild.maven.tasks.MavenPublishTask;
import de.m_marvin.metabuild.maven.tasks.MavenResolveTask;

public class CppMultiTargetBuildScript extends BuildScript {
	
	public class TargetConfig {
		public MavenResolveTask dependencies;
		public CppCompileTask compileCpp;
		public CppLinkTask linkCpp;
		public ZipTask headersZip;
		public ZipTask sourcesZip;
		public BuildTask build;
		public MavenPublishTask publishMaven;
	}
	
	public final Map<String, TargetConfig> targets = new HashMap<>();
	
	public String projectName = "Project";
	
	public File sources = new File("src/cpp/source");
	public File headers = new File("src/cpp/header");
	public File publics = new File("src/cpp/public");
	
	public boolean withSources = false;
//	public final List<File> includes = new ArrayList<File>();
//	public final List<File> libraryDirs = new ArrayList<>();
//	public final List<String> libraries = new ArrayList<String>();
	public String sourceStandard = null;
	
	public TargetConfig target(String name) {
		return this.targets.get(name);
	}
	
	@Override
	public void init() {
		
		super.init();
		
		BuildTask build = new BuildTask("build");
		build.group = "build";
		build.dependsOn(this.targets.values().stream().map(t -> t.build).toArray(BuildTask[]::new));

		BuildTask publishMaven = new BuildTask("publishMaven");
		publishMaven.group = "publish";
		publishMaven.dependsOn(this.targets.values().stream().map(t -> t.publishMaven).toArray(BuildTask[]::new));
		
		new FileTask("clean", Action.DELETE, new File("build"));
		
	}
	
	public Collection<File> dependencyHeaders() {
		return null;
	}
	
	public void withSourcesZip() {
		this.withSources = true;
	}
	
	public TargetConfig makeTarget(String name, String binName) {
		
		TargetConfig target = new TargetConfig();
		this.targets.put(name, target);
		
		target.dependencies = new MavenResolveTask("cppDependencies" + name);
		target.dependencies.group = "dependencies";
		target.dependencies.fpCompiletime = new File("build/" + name + "/compile.filepath");
		target.dependencies.fpRunttime = new File("build/" + name + "/runtime.filepath");
		target.dependencies.fpTestCompiletime = new File("build/" + name + "/testcompile.filepath");
		target.dependencies.fpTestRuntime = new File("build/" + name + "/testruntime.filepath");
		
		repositories(target.dependencies, name);
		dependenciesa(target.dependencies, name);
		
		target.compileCpp = new CppCompileTask("compileCpp" + name);
		target.compileCpp.group = "build";
		target.compileCpp.includes.add(target.dependencies.fpCompiletime);
		target.compileCpp.includes.add(this.headers);
		target.compileCpp.includes.add(this.publics);
		target.compileCpp.sourcesDir = this.sources;
		target.compileCpp.sourceStandard = this.sourceStandard;
		target.compileCpp.objectsDir = new File("build/" + name + "/objects/cpp/");
		target.compileCpp.dependsOn(target.dependencies);
		
		target.linkCpp = new CppLinkTask("linkCpp" + name);
		target.linkCpp.group = "build";
		target.linkCpp.objectsDir = target.compileCpp.objectsDir;
		target.linkCpp.outputFile = new File("build/" + name + "/bin/" + binName);
		target.linkCpp.dependsOn(target.compileCpp);
		
		if (this.withSources) {

			target.sourcesZip = new ZipTask("sourcesZip" + name);
			target.sourcesZip.group = "build";
			target.sourcesZip.entries.put(this.sources, "");
			target.sourcesZip.entries.put(this.headers, "");
			target.sourcesZip.entries.put(this.publics, "");
			target.sourcesZip.archive = new File("build/" + name + "/bin/" + FileUtility.getNameNoExtension(new File(binName)) + "-sources.zip");
			
		}
		
		target.headersZip = new ZipTask("headersZip" + name);
		target.headersZip.group = "build";
		target.headersZip.entries.put(this.publics, "");
		target.headersZip.archive = new File("build/" + name + "/bin/" + FileUtility.getNameNoExtension(new File(binName)) + "-headers.zip");
		publicHeaders(target.headersZip, name);

		target.build = new BuildTask("build" + name);
		target.build.group = "build";
		target.build.dependsOn(target.linkCpp, target.headersZip);
		if (this.withSources)
			target.build.dependsOn(target.sourcesZip);
		
		target.publishMaven = new MavenPublishTask("publishMaven" + name);
		target.publishMaven.group = "publish";
		target.publishMaven.artifact("bin", target.linkCpp.outputFile);
		if (this.withSources)
			target.publishMaven.artifact("sources", target.sourcesZip.archive);
		target.publishMaven.artifact("headers", target.headersZip.archive);
		publishing(target.publishMaven, name);
		target.publishMaven.dependsOn(target.build);
		
		return target;
		
	}
	
	public void repositories(MavenResolveTask dependencies, String config) {
		
	}
	
	public void dependenciesa(MavenResolveTask dependencies, String config) {
		
	}
	
	public void publicHeaders(ZipTask headersZip, String config) {
		
	}
	
	public void publishing(MavenPublishTask publish, String config) {
			
	}
	
}
