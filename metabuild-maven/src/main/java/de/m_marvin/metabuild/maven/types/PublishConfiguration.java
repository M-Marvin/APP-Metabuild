package de.m_marvin.metabuild.maven.types;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PublishConfiguration {
	
	public Map<String, File> artifacts = new HashMap<String, File>();
	public DependencyGraph dependencies;
	public Set<Repository> repositories = new HashSet<Repository>();
	public Artifact coordinates;
	public ZonedDateTime timeOfCreation;
	
}
