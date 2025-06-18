package de.m_marvin.metabuild.maven.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	
	public File fpCompiletime = new File("compile.filepath");
	public File fpRunttime = new File("runtime.filepath");
	public File fpTestCompiletime = new File("testcompile.filepath");
	public File fpTestRuntime = new File("testruntime.filepath");
	public final DependencyGraph graph;
	protected final MavenResolver resolver;
	
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
		Objects.requireNonNull(repository);
		this.graph.addRepository(repository);
	}
	
	public Collection<Repository> getRepositories() {
		return this.graph.getRepositories();
	}
	
	protected void dependency(Scope scope, Artifact artifact, String systemPath, boolean optional) {
		Objects.requireNonNull(artifact);
		Objects.requireNonNull(scope);
		this.graph.addTransitive(scope.mavenScope(), artifact, null, systemPath, optional);
	}
	
	protected void dependency(Scope scope, String artifact, String systemPath, boolean optional) {
		Objects.requireNonNull(scope);
		Objects.requireNonNull(artifact);
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
		Objects.requireNonNull(task);
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
		Collection<File> dependencyPath = FileUtility.loadFilePath(FileUtility.absolute(this.fpTestCompiletime));
		if (dependencyPath == null) return Collections.emptyList();
		return dependencyPath.stream()
				.map(File::getParentFile)
				.distinct()
				.map(f -> Stream.of(f.listFiles()).filter(dependencyPath::contains).toArray(File[]::new))
				.toList();
	}
	
	protected List<File> attemptResolution(DependencyScope scope) throws MavenException {
		List<File> filepath = new ArrayList<File>();
		Map<Artifact, Integer> effectiveDependencies = new HashMap<Artifact, Integer>();
		if (!this.resolver.resolveGraph(this.graph, a -> false, effectiveDependencies, 0, scope)) return null;
		if (!this.resolver.downloadArtifacts(this.graph, effectiveDependencies.keySet(), filepath, scope)) return null;
		return filepath;
	}
	
	@Override
	protected TaskState prepare() {
		if (Metabuild.get().isRefreshDependencies()) return TaskState.OUTDATED;

		logger().infot(logTag(), "attempt offline cache resolution to verify available dependencies ...");
		
		// Test if dependencies are missing in cache
		try {
			this.resolver.setResolutionStrategy(ResolutionStrategy.OFFLINE);
			List<File> filepath;
			
			filepath = attemptResolution(DependencyScope.COMPILETIME);
			if (filepath == null) {
				logger().infot(logTag(), "offline cache incomplete, COMPILETIME missing files, request remote resolution");
				return TaskState.OUTDATED;
			} else if (!verifyFilepathFile(this.fpCompiletime, filepath)) {
				logger().infot(logTag(), "offline cache incomplete, COMPILETIME filepath mismatch, request remote resolution");
				return TaskState.OUTDATED;
			}
			

			filepath = attemptResolution(DependencyScope.RUNTIME);
			if (filepath == null) {
				logger().infot(logTag(), "offline cache incomplete, RUNTIME missing files, request remote resolution");
				return TaskState.OUTDATED;
			} else if (!verifyFilepathFile(this.fpRunttime, filepath)) {
				logger().infot(logTag(), "offline cache incomplete, RUNTIME filepath mismatch, request remote resolution");
				return TaskState.OUTDATED;
			}

			filepath = attemptResolution(DependencyScope.TEST_COMPILETIME);
			if (filepath == null) {
				logger().infot(logTag(), "offline cache incomplete, TEST_COMPILETIME missing files, request remote resolution");
				return TaskState.OUTDATED;
			} else if (!verifyFilepathFile(this.fpTestCompiletime, filepath)) {
				logger().infot(logTag(), "offline cache incomplete, TEST_COMPILETIME filepath mismatch, request remote resolution");
				return TaskState.OUTDATED;
			}

			filepath = attemptResolution(DependencyScope.TEST_RUNTIME);
			if (filepath == null) {
				logger().infot(logTag(), "offline cache incomplete, TEST_RUNTIME missing files, request remote resolution");
				return TaskState.OUTDATED;
			} else if (!verifyFilepathFile(this.fpTestRuntime, filepath)) {
				logger().infot(logTag(), "offline cache incomplete, TEST_RUNTIME filepath mismatch, request remote resolution");
				return TaskState.OUTDATED;
			}
		} catch (MavenException e) {
			throw BuildException.msg(e, "maven resolution error!");
		}

		logger().infot(logTag(), "offline cache verified, all dependencies available");
		
		return TaskState.UPTODATE;
	}
	
	protected boolean verifyFilepathFile(File filepathFile, List<File> filepath) {
		Collection<File> filepath1 = FileUtility.loadFilePath(FileUtility.absolute(filepathFile));
		if (filepath1 == null) return false;
		long c1 = filepath.stream().distinct().count();
		long c2 = filepath1.stream().distinct().count();
		long m = filepath1.stream().filter(filepath::contains).distinct().count();
		return c1 == c2 && c1 == m;
	}
	
	protected void writeFilepathFile(File filepathFile, List<File> filepath) {
		File f = FileUtility.absolute(filepathFile);
		if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs())
			throw BuildException.msg("unable to create destination folder for dependency filepath: %s", f);
		FileUtility.writeFilePath(f, filepath);
	}
	
	@Override
	protected boolean run() {
		
		try {
			this.resolver.setResolutionStrategy(Metabuild.get().isRefreshDependencies() ? ResolutionStrategy.FORCE_REMOTE : ResolutionStrategy.REMOTE);
			List<File> filepath;
			
			filepath = attemptResolution(DependencyScope.COMPILETIME);
			if (filepath == null) {
				logger().errort(logTag(), "unable to resolve COMPILETIME dependencies!");
				return false;
			} 
			writeFilepathFile(this.fpCompiletime, filepath);
			
			filepath = attemptResolution(DependencyScope.RUNTIME);
			if (filepath == null) {
				logger().errort(logTag(), "unable to resolve RUNTIME dependencies!");
				return false;
			} 
			writeFilepathFile(this.fpRunttime, filepath);
			
			filepath = attemptResolution(DependencyScope.TEST_COMPILETIME);
			if (filepath == null) {
				logger().errort(logTag(), "unable to resolve TEST_COMPILETIME dependencies!");
				return false;
			} 
			writeFilepathFile(this.fpTestCompiletime, filepath);
			
			filepath = attemptResolution(DependencyScope.TEST_RUNTIME);
			if (filepath == null) {
				logger().errort(logTag(), "unable to resolve TEST_RUNTIME dependencies!");
				return false;
			} 
			writeFilepathFile(this.fpTestRuntime, filepath);
		} catch (MavenException e) {
			throw BuildException.msg(e, "unexpected error while resolving maven dependencies!");
		}
		
		return true;
	}
	
}
