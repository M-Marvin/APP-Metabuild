package de.m_marvin.metabuild.maven.tasks;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.handler.MavenPublisher;
import de.m_marvin.metabuild.maven.handler.MavenResolver;
import de.m_marvin.metabuild.maven.handler.MavenResolver.ResolutionStrategy;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.PublishConfiguration;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.xml.POM;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenPublishTask extends BuildTask {

	public final Set<Repository> repositories = new HashSet<Repository>();
	public final Map<String, File> artifacts = new HashMap<String, File>();
	public Artifact coordinates = null;
	protected ZonedDateTime timeOfCreation = null;
	protected final Set<DependencyGraph> dependencies = new HashSet<>();
	protected final MavenResolver resolver;
	protected final MavenPublisher publisher;
	protected final Set<Repository> repositoriesToUpdate = new HashSet<Repository>();
	
	public MavenPublishTask(String name) {
		super(name);
		this.type = TaskType.named("MAVEN_PUBLISH");
		
		try {
			this.resolver = new MavenResolver(new TagLogger(logger(), logTag() + "/resolver"), new File(Metabuild.get().cacheDir(), "files"));
			this.publisher = new MavenPublisher(new TagLogger(logger(), logTag() + "/publisher"), this.resolver);
			this.publisher.setStatusCallback(this::status);
		} catch (Exception e) {
			throw BuildScriptException.msg(e, "unable to initialize maven dependency resolver and publisher!");
		}
	}
	
	public void dependencies(MavenResolveTask dependencyTask) {
		Objects.requireNonNull(dependencyTask);
		Objects.requireNonNull(dependencyTask.graph);
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
		
		// try to acquire the timestamp of the newest artifact file
		this.resolver.setResolutionStrategy(ResolutionStrategy.FORCE_REMOTE);
		Instant newestTime = null;
		for (File artifact : this.artifacts.values()) {
			Optional<FileTime> artifactTime = FileUtility.timestamp(FileUtility.absolute(artifact));
			if (artifactTime.isPresent()) {
				Instant time = artifactTime.get().toInstant();
				newestTime = (newestTime == null || newestTime.compareTo(time) < 0) ? time : newestTime;
			}
		}
		this.timeOfCreation = newestTime == null ? Instant.now().atZone(ZoneOffset.UTC) : ZonedDateTime.ofInstant(newestTime, ZoneOffset.UTC);
		
		if (this.repositories.size() == 0 || this.artifacts.size() == 0)
			return TaskState.UPTODATE;
		
		if (this.coordinates == null)
			throw BuildException.msg("publishing coordinates not set");

		logger().infot(logTag(), "attempt remote resolution to verify already uploaded artifacts ...");
		
		// check to which repositories an upload has to be made
		this.repositoriesToUpdate.clear();
		for (Repository repository : this.repositories) {
			try {
				POM pom = this.resolver.downloadArtifactPOM(repository, this.coordinates);
				if (pom != null) {
					if (!this.coordinates.isSnapshot()) continue;
					ZonedDateTime timestamp = this.resolver.getLastSnapshotVersion();
					// compare timestamps, add 5 second buffer to avoid rounding errors
					if (timestamp != null && this.timeOfCreation.compareTo(timestamp.plusSeconds(5)) < 0)
						continue;
				}

				logger().infot(logTag(), "=> upload to repository required: " + repository);
				
				this.repositoriesToUpdate.add(repository);
			} catch (MavenException e) {
				throw BuildException.msg(e, "unable to check current repository state before upload: %s", repository);
			}
		}

		if (!this.repositoriesToUpdate.isEmpty()) {
			logger().infot(logTag(), "uploads required, request publish task to run");
		} else {
			logger().infot(logTag(), "artifacts all already uploaded");
		}
		
		return this.repositoriesToUpdate.isEmpty() ? TaskState.UPTODATE : TaskState.OUTDATED;
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
		configuration.repositories = this.repositoriesToUpdate;
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
