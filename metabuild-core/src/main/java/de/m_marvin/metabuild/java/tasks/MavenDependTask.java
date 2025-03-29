package de.m_marvin.metabuild.java.tasks;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.java.maven.DependencyResolver;
import de.m_marvin.metabuild.java.maven.DependencyResolver.QueryMode;
import de.m_marvin.metabuild.java.maven.MavenResolver.MavenRepository;
import de.m_marvin.metabuild.java.maven.MavenResolver.POM.Scope;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenDependTask extends BuildTask {
	
	protected static record Dependency(String dependency, String[] configurations) {}
	
	public Predicate<Scope> scope = s -> s == Scope.COMPILE || s == Scope.RUNTIME;
	public File classpath = new File(".classpath");
	
	protected DependencyResolver resolver;
	
	public MavenDependTask(String name) {
		super(name);
		this.type = TaskType.named("JAVA_DEPENDENCY");
		
		try {
			this.resolver = new DependencyResolver(new File(Metabuild.get().cacheDir(), "files"), new TagLogger(Metabuild.get().logger(), logTag()));
		} catch (Exception e) {
			throw BuildScriptException.msg(e, "unable to initialize maven dependency resolver!");
		}
	}
	
	public List<File> getClasspathEntries() {
		return this.resolver.getDependencyJarPaths().values().stream()
			.flatMap(f -> Stream.of(f.listFiles())
					.filter(f2 -> FileUtility.getExtension(f2).equalsIgnoreCase("jar")))
			.toList();
	}

	public List<File[]> getDependencyEntries() {
		return this.resolver.getDependencyJarPaths().values().stream()
			.map(f -> f.listFiles())
			.toList();
	}
	
	public String getClasspathString() {
		StringBuffer buf = new StringBuffer();
		getClasspathEntries().forEach(f -> buf.append(f.getAbsolutePath()).append(";"));
		return buf.toString();
	}
	
	public void repository(MavenRepository repository) {
		this.resolver.addRepository(repository);
	}
	
	public void extendsFrom(MavenDependTask task) {
		for (var repo : task.resolver.getRepositories()) {
			this.resolver.addRepository(repo);
		}
		for (var dep : task.resolver.getDependencies()) {
			this.resolver.addDependency(dep.dependency(), dep.configurations());
		}
	}
	
	public void add(String dependencyStr, String... configurations) {
		try {
			this.resolver.addDependency(dependencyStr, configurations);
		} catch (IllegalArgumentException e) {
			throw BuildScriptException.msg(e, "invalid dependency: %s", dependencyStr);
		}
	}
	
	@Override
	protected TaskState prepare() {
		if (Metabuild.get().isRefreshDependencies()) return TaskState.OUTDATED;

		// Test if dependencies are missing in cache
		try {
			this.resolver.resolveDependencies(this.scope, QueryMode.CACHE_ONLY, null);
		} catch (MetaScriptException e) {
			return TaskState.OUTDATED;
		}
		
		// Test if jar files have chaned since last classpath file
		File classpathFile = FileUtility.absolute(this.classpath);
		Optional<FileTime> classpathTimestamp = FileUtility.timestamp(classpathFile);
		if (classpathTimestamp.isEmpty())
			return TaskState.OUTDATED;
		for (File entry : getClasspathEntries()) {
			Optional<FileTime> t = FileUtility.timestamp(entry);
			if (t.isPresent() && classpathTimestamp.get().compareTo(t.get()) < 0)
				return TaskState.OUTDATED;
		}

		// Test if classpath file matches
		String classpath = FileUtility.readFileUTF(classpathFile);
		if (classpath == null) return TaskState.OUTDATED;
		if (!classpath.equals(getClasspathString())) return TaskState.OUTDATED;
		
		return TaskState.UPTODATE;
	}
	
	protected static Set<String> refreshedArtifacts = new HashSet<>();
	
	protected static QueryMode verifyNeedsRefresh(String artifact) {
		if (!refreshedArtifacts.contains(artifact)) {
			refreshedArtifacts.add(artifact);
			return QueryMode.ONLINE_ONLY;
		}
		return QueryMode.CACHE_ONLY;
	}
	
	@Override
	protected void cleanup() {
		refreshedArtifacts.clear();
	}
	
	@Override
	protected boolean run() {
		this.resolver.resolveDependencies(
				this.scope, 
				Metabuild.get().isRefreshDependencies() ? MavenDependTask::verifyNeedsRefresh : artifact -> QueryMode.CACHE_AND_ONLINE,
				dep -> status("resolving > " + dep));
		
		File classpathFile = FileUtility.absolute(this.classpath);
		if (!classpathFile.getParentFile().isDirectory() && !classpathFile.getParentFile().mkdirs()) {
			logger().errort(logTag(), "failed to create output directory for classpath file: %s", classpathFile.getParentFile());
			return false;
		}
		
		FileUtility.writeFileUTF(classpathFile, getClasspathString());
		return true;
	}
	
}
