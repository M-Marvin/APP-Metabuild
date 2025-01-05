package de.m_marvin.metabuild.tasks.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.DependencyResolver;
import de.m_marvin.metabuild.maven.DependencyResolver.QueryMode;
import de.m_marvin.metabuild.maven.MavenResolver.MavenRepository;
import de.m_marvin.metabuild.maven.MavenResolver.POM.Scope;
import de.m_marvin.metabuild.tasks.BuildTask;
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
		if (!FileUtility.absolute(this.classpath).isFile()) return TaskState.OUTDATED;
		try {
			this.resolver.resolveDependencies(this.scope, QueryMode.CACHE_ONLY);
			return TaskState.UPTODATE;
		} catch (MetaScriptException e) {
			return TaskState.OUTDATED;
		}
	}
	
	@Override
	protected boolean run() {
		this.resolver.resolveDependencies(this.scope, Metabuild.get().isRefreshDependencies() ? QueryMode.ONLINE_ONLY : QueryMode.CACHE_AND_ONLINE);
		
		File classpathFile = FileUtility.absolute(this.classpath);
		if (!classpathFile.getParentFile().isDirectory() && !classpathFile.getParentFile().mkdirs()) {
			logger().errort(logTag(), "failed to create output directory for classpath file: %s", classpathFile.getParentFile());
			return false;
		}
		
		try {
			OutputStream os = new FileOutputStream(classpathFile);
			os.write(getClasspathString().getBytes(StandardCharsets.UTF_8));
			os.close();
			return true;
		} catch (IOException e) {
			throw BuildException.msg("failed to create classpath file: %s", this.classpath);
		}
	}
	
}
