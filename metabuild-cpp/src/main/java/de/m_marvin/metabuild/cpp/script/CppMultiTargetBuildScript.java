package de.m_marvin.metabuild.cpp.script;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.FileTask;
import de.m_marvin.metabuild.core.tasks.UnZipTask;
import de.m_marvin.metabuild.core.tasks.FileTask.Action;
import de.m_marvin.metabuild.core.tasks.ZipTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.cpp.tasks.CppCompileTask;
import de.m_marvin.metabuild.cpp.tasks.CppLinkTask;
import de.m_marvin.metabuild.maven.tasks.MavenPublishTask;
import de.m_marvin.metabuild.maven.tasks.MavenResolveTask;

public class CppMultiTargetBuildScript extends BuildScript {
	
	public class TargetConfig {
		public MavenResolveTask dependencies;
		public UnZipTask headersUnzip;
		public UnZipTask binaryUnzip;
		public CppCompileTask compileCpp;
		public CppLinkTask linkCpp;
		public ZipTask binaryZip;
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
	
	public Predicate<File> headerPredicate = f -> {
		String ext = FileUtility.getExtension(f);
		return ext.equalsIgnoreCase("h") || ext.equalsIgnoreCase("hpp");
	};
	
	public Predicate<File> sourcePredicate = f -> {
		String ext = FileUtility.getExtension(f);
		return ext.equalsIgnoreCase("c") || ext.equalsIgnoreCase("cpp");
	};
	
	public Predicate<File> binaryPredicate = f -> {
		return !headerPredicate.test(f) && !sourcePredicate.test(f);
	};
	
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

		BuildTask dependencies = new BuildTask("dependencies");
		dependencies.group = "dependencies";
		dependencies.dependsOn(this.targets.values().stream().map(t -> t.dependencies).toArray(BuildTask[]::new));
		
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
		
		String outputName = FileUtility.getNameNoExtension(new File(binName));
		
		target.dependencies = new MavenResolveTask("cppDependencies" + name);
		target.dependencies.group = "platform";
		target.dependencies.fpCompiletime = new File("build/" + name + "/compile.filepath");
		target.dependencies.fpRunttime = new File("build/" + name + "/runtime.filepath");
		target.dependencies.fpTestCompiletime = new File("build/" + name + "/testcompile.filepath");
		target.dependencies.fpTestRuntime = new File("build/" + name + "/testruntime.filepath");
		
		repositories(target.dependencies, name);
		dependenciesa(target.dependencies, name);
		
		target.headersUnzip = new UnZipTask("headersUnzip" + name);
		target.headersUnzip.group = "platform";
		target.headersUnzip.archives.add(target.dependencies.fpCompiletime);
		target.headersUnzip.extractPredicate = headerPredicate;
		target.headersUnzip.output = new File("build/" + name + "/includeHeaders");
		target.headersUnzip.dependsOn(target.dependencies);

		target.binaryUnzip = new UnZipTask("binaryUnzip" + name);
		target.binaryUnzip.group = "platform";
		target.binaryUnzip.archives.add(target.dependencies.fpCompiletime);
		target.binaryUnzip.extractPredicate = binaryPredicate;
		target.binaryUnzip.output = new File("build/" + name + "/includeLibraries");
		target.binaryUnzip.dependsOn(target.dependencies);
		
		target.compileCpp = new CppCompileTask("compileCpp" + name);
		target.compileCpp.group = "platform";
		target.compileCpp.includes.add(target.headersUnzip.output);
		target.compileCpp.includes.add(this.headers);
		target.compileCpp.includes.add(this.publics);
		target.compileCpp.sourcesDir = this.sources;
		target.compileCpp.sourceStandard = this.sourceStandard;
		target.compileCpp.objectsDir = new File("build/" + name + "/objects/cpp/");
		target.compileCpp.dependsOn(target.dependencies, target.headersUnzip);
		
		target.linkCpp = new CppLinkTask("linkCpp" + name);
		target.linkCpp.group = "platform";
		target.linkCpp.objectsDir = target.compileCpp.objectsDir;
		target.linkCpp.libraryDirs.add(target.binaryUnzip.output);
		target.linkCpp.outputFile = new File("build/" + name + "/bin/" + binName);
		target.linkCpp.dependsOn(target.compileCpp, target.binaryUnzip);
		
		linking(target.linkCpp, name);
		
		target.binaryZip = new ZipTask("binaryZip" + name);
		target.binaryZip.group = "platform";
		target.binaryZip.entries.put(target.linkCpp.outputFile, binName);
		target.binaryZip.archive = new File("build/" + name + "/bin/" + outputName + ".zip");
		target.binaryZip.dependsOn(target.linkCpp);
		
		if (this.withSources) {

			target.sourcesZip = new ZipTask("sourcesZip" + name);
			target.sourcesZip.group = "platform";
			target.sourcesZip.entries.put(this.sources, "");
			target.sourcesZip.entries.put(this.headers, "");
			target.sourcesZip.entries.put(this.publics, "");
			target.sourcesZip.archive = new File("build/" + name + "/bin/" + outputName + "-sources.zip");
			
		}
		
		target.headersZip = new ZipTask("headersZip" + name);
		target.headersZip.group = "platform";
		target.headersZip.entries.put(this.publics, "");
		target.headersZip.archive = new File("build/" + name + "/bin/" + outputName + "-headers.zip");
		
		publicHeaders(target.headersZip, name);

		target.build = new BuildTask("build" + name);
		target.build.group = "platform";
		target.build.dependsOn(target.binaryZip, target.headersZip);
		if (this.withSources)
			target.build.dependsOn(target.sourcesZip);
		
		target.publishMaven = new MavenPublishTask("publishMaven" + name);
		target.publishMaven.group = "platform";
		target.publishMaven.dependencies(target.dependencies);
		target.publishMaven.artifact("", target.binaryZip.archive);
		if (this.withSources)
			target.publishMaven.artifact("sources", target.sourcesZip.archive);
		target.publishMaven.artifact("headers", target.headersZip.archive);
		target.publishMaven.dependsOn(target.build);
		
		publishing(target.publishMaven, name);
		
		return target;
		
	}
	
	public void repositories(MavenResolveTask dependencies, String config) {
		
	}
	
	public void dependenciesa(MavenResolveTask dependencies, String config) {
		
	}
	
	public void linking(CppLinkTask linker, String config) {
		
	}
	
	public void publicHeaders(ZipTask headersZip, String config) {
		
	}
	
	public void publishing(MavenPublishTask publish, String config) {
			
	}
	
}
