package de.m_marvin.metabuild.maven.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.handler.MavenResolver;
import de.m_marvin.metabuild.maven.handler.MavenResolver.ResolutionStrategy;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.DependencyScope;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Scope;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenResolveTask extends BuildTask {
	
	public File cpCompiletime = new File("compile.classpath");
	public File cpRunttime = new File("runtime.classpath");
	public File cpTestCompiletime = new File("testcompile.classpath");
	public File cpTestRuntime = new File("testruntime.classpath");
	protected DependencyGraph graph;
	protected MavenResolver resolver;
	
	public MavenResolveTask(String name) {
		super(name);
		this.type = TaskType.named("MAVEN_DEPENDENCY");
		
		try {
			this.graph = new DependencyGraph();
			this.resolver = new MavenResolver(new TagLogger(logger(), logTag()), new File(Metabuild.get().cacheDir(), "files"));
			this.resolver.setStatusCallback(this::status);
		} catch (Exception e) {
			throw BuildScriptException.msg(e, "unable to initialize maven dependency resolver!");
		}
	}
	
	public void repository(Repository repository) {
		this.graph.addRepository(repository);
	}
	
	public Collection<Repository> getRepositories() {
		return this.graph.getRepositories();
	}
	
	protected void dependency(Scope scope, Artifact artifact, String systemPath, boolean optional) {
		this.graph.addTransitive(scope.mavenScope(), artifact, null, systemPath, optional);
	}
	
	protected void dependency(Scope scope, String artifact, String systemPath, boolean optional) {
		try {
			dependency(scope, Artifact.of(artifact), systemPath, optional);
		} catch (MavenException e) {
			throw BuildScriptException.msg(e, "malformed maven coordinates: %s", artifact);
		}
	}
	
	public void implementation(String dependency) {
		dependency(Scope.COMPILE, dependency, null, false);
	}
	
	public void runtime(String dependency) {
		dependency(Scope.RUNTIME, dependency, null, false);
	}
	
	public void test(String dependency) {
		dependency(Scope.TEST, dependency, null, false);
	}
	
	public void provided(String dependency) {
		dependency(Scope.PROVIDED, dependency, null, false);
	}
	
	public void system(String dependency, String systemPath) {
		dependency(Scope.SYSTEM, dependency, systemPath, false);
	}
	
	public void implementationOpt(String dependency) {
		dependency(Scope.COMPILE, dependency, null, true);
	}
	
	public void runtimeOpt(String dependency) {
		dependency(Scope.RUNTIME, dependency, null, true);
	}
	
	public void testOpt(String dependency) {
		dependency(Scope.TEST, dependency, null, true);
	}
	
	public void providedOpt(String dependency) {
		dependency(Scope.PROVIDED, dependency, null, true);
	}
	
	public void systemOpt(String dependency, String systemPath) {
		dependency(Scope.SYSTEM, dependency, systemPath, true);
	}
	
	public void extendsFrom(MavenResolveTask task) {
		for (var repo : task.graph.getRepositories()) {
			this.graph.addRepository(repo);
		}
		for (var group : task.graph.getTransitiveGroups()) {
			for (var dep : group.artifacts) {
				this.graph.addTransitive(group.scope, dep.artifact, group.excludes, dep.systemPath, dep.optional);
			}
		}
	}
	
	public void setSnapshotResolutionInterval(long expiration, TimeUnit timeUnit) {
		this.resolver.setMetadataExpiration(expiration, timeUnit);
	}
	
	public void setRemoteTimeout(long timeout, TimeUnit timeUnit) {
		this.resolver.setRemoteTimeout(timeout, timeUnit);
	}
	
	public List<File[]> getDependencyEntries() {
		try {
			List<File> dependencies = new ArrayList<File>();
			this.resolver.resolveGraph(this.graph, a -> false, dependencies, DependencyScope.TEST_COMPILETIME);
			
			return dependencies.stream()
					.map(File::getParentFile)
					.distinct()
					.map(f -> Stream.of(f.listFiles()).filter(dependencies::contains).toArray(File[]::new))
					.toList();
		} catch (MavenException e) {
			return Collections.emptyList();
		}
	}
	
	@Override
	protected TaskState prepare() {
		if (Metabuild.get().isRefreshDependencies()) return TaskState.OUTDATED;

		// Test if dependencies are missing in cache
		try {
			this.resolver.setResolutionStrategy(ResolutionStrategy.OFFLINE);
			List<File> classpath = new ArrayList<File>();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.COMPILETIME)) return TaskState.OUTDATED;
			if (!verifyClasspathFile(this.cpCompiletime, classpath)) return TaskState.OUTDATED;
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.RUNTIME)) return TaskState.OUTDATED;
			if (!verifyClasspathFile(this.cpRunttime, classpath)) return TaskState.OUTDATED;
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.TEST_COMPILETIME)) return TaskState.OUTDATED;
			if (!verifyClasspathFile(this.cpTestCompiletime, classpath)) return TaskState.OUTDATED;
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.TEST_RUNTIME)) return TaskState.OUTDATED;
			if (!verifyClasspathFile(this.cpTestRuntime, classpath)) return TaskState.OUTDATED;
		} catch (MavenException e) {
			throw BuildException.msg(e, "maven resolution error!");
		}
		
		// Check if snapshot metadata needs to be updated from remote
		if (this.resolver.isMetaExpired()) return TaskState.OUTDATED;
		
		return TaskState.UPTODATE;
	}
	
	protected boolean verifyClasspathFile(File classpathFile, List<File> classpath) {
		String classpathStr = FileUtility.readFileUTF(FileUtility.absolute(classpathFile));
		if (classpathStr == null) return false;
		String classpathStr2 = classpath.stream().map(File::getAbsolutePath).reduce((a, b) -> a + ";" + b).orElse("");
		return classpathStr.equals(classpathStr2);
	}
	
	protected void writeClasspathFile(File classpathFile, List<File> classpath) {
		String classpathStr = classpath.stream().map(File::getAbsolutePath).reduce((a, b) -> a + ";" + b).orElse("");
		File f = FileUtility.absolute(classpathFile);
		if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs())
			throw BuildException.msg("unable to create destination folder for dependency classpath: %s", f);
		FileUtility.writeFileUTF(f, classpathStr);
	}
	
	@Override
	protected boolean run() {
		
		try {
			this.resolver.setResolutionStrategy(Metabuild.get().isRefreshDependencies() ? ResolutionStrategy.FORCE_REMOTE : ResolutionStrategy.REMOTE);
			List<File> classpath = new ArrayList<File>();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.COMPILETIME)) {
				logger().errort(logTag(), "unable to resolve compiletime dependencies!");
				return false;
			}
			writeClasspathFile(this.cpCompiletime, classpath);
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.RUNTIME)) {
				logger().error(logTag(), "unable to resolve runtime dependencies!");
				return false;
			}
			writeClasspathFile(this.cpRunttime, classpath);
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.TEST_COMPILETIME)) {
				logger().error(logTag(), "unable to resolve test compiletime dependencies!");
				return false;
			}
			writeClasspathFile(this.cpTestCompiletime, classpath);
			classpath.clear();
			if (!this.resolver.resolveGraph(this.graph, a -> false, classpath, DependencyScope.TEST_RUNTIME)) {
				logger().error(logTag(), "unable to resolve test runtime dependencies!");
				return false;
			}
			writeClasspathFile(this.cpTestRuntime, classpath);
		} catch (MavenException e) {
			throw BuildException.msg(e, "unexpected error while resolving maven dependencies!");
		}
		
		return true;
	}
	
}
