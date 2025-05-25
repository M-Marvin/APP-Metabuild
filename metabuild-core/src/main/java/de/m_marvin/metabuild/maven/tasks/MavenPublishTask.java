package de.m_marvin.metabuild.maven.tasks;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.handler.MavenPublisher;
import de.m_marvin.metabuild.maven.handler.MavenResolver;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.PublishConfiguration;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenPublishTask extends BuildTask {

	public final Set<Repository> repositories = new HashSet<Repository>();
	public final Map<String, File> artifacts = new HashMap<String, File>();
	public Artifact coordinates = null;
	public ZonedDateTime timeOfCreation = null;
	protected final Set<DependencyGraph> dependencies = new HashSet<>();
	protected final MavenResolver resolver;
	protected final MavenPublisher publisher;
	
	public MavenPublishTask(String name) {
		super(name);
		
		try {
			this.resolver = new MavenResolver(new TagLogger(logger(), logTag()), new File(Metabuild.get().cacheDir(), "files"));
			this.resolver.setStatusCallback(this::status);
			this.publisher = new MavenPublisher(new TagLogger(logger(), logTag()), this.resolver);
			this.publisher.setStatusCallback(this::status);
		} catch (Exception e) {
			throw BuildScriptException.msg(e, "unable to initialize maven dependency resolver and publisher!");
		}
	}
	
	public void dependencies(MavenResolveTask dependencyTask) {
		Objects.requireNonNull(dependencyTask);
		this.dependencies.add(dependencyTask.graph);
	}
	
	public void repository(Repository repository) {
		Objects.requireNonNull(repository);
		this.repositories.add(repository);
	}
	
	public Set<Repository> getRepositories() {
		return repositories;
	}
	
	public void artifact(String config, File file) {
		Objects.requireNonNull(file);
		Objects.requireNonNull(config);
		this.artifacts.put(config, file);
	}
	
	public Collection<File> getArtifacts() {
		return artifacts.values();
	}
	
	public void coordinates(String artifact) {
		Objects.requireNonNull(artifact);
		try {
			this.coordinates = Artifact.of(artifact);
		} catch (MavenException e) {
			throw BuildScriptException.msg(e, "malformed maven coordinates: %s", artifact);
		}
	}
	
	public String getCoordinates() {
		return coordinates.toString();
	}

	public void setRemoteTimeout(long timeout, TimeUnit timeUnit) {
		this.publisher.setRemoteTimeout(timeout, timeUnit);
	}
	
	@Override
	protected TaskState prepare() {
		
		// default timestamp to "now"
		if (this.timeOfCreation == null)
			this.timeOfCreation = Instant.now().atZone(ZoneOffset.UTC);
		
		if (this.repositories.size() == 0 || this.artifacts.size() == 0)
			return TaskState.UPTODATE;
		
		if (this.coordinates == null)
			throw BuildException.msg("publishing coordinates not set");
		
		// always run publish tasks if requested to
		return TaskState.OUTDATED;
	}
	
	@Override
	protected boolean run() {
		
		// combine dependency graphs to one
		DependencyGraph transitiveGraph = new DependencyGraph();
		transitiveGraph.importAll(this.dependencies);
		
		// configure publish job
		PublishConfiguration configuration = new PublishConfiguration();
		configuration.artifacts = this.artifacts.keySet().stream().collect(Collectors.toMap(k -> k, k -> FileUtility.absolute(this.artifacts.get(k))));
		configuration.coordinates = this.coordinates;
		configuration.dependencies = transitiveGraph;
		configuration.repositories = this.repositories;
		configuration.timeOfCreation = this.timeOfCreation;
		
		// run publication
		try {
			boolean success = this.publisher.publishConfiguration(configuration);
			
			if (!success)
				logger().warn(logTag(), "publication did not complete, some targets might have been uploaded!");
			return success;
		} catch (MavenException e) {
			throw BuildException.msg(e, "unexpected error during publish job, some targets might have been uploaded!");
		}
		
	}
	
}
