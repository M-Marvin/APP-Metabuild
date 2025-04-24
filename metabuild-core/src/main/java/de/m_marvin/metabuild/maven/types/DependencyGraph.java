package de.m_marvin.metabuild.maven.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DependencyGraph {
	
	private static class TransitiveGroup {
		public final Set<TransitiveEntry> artifacts = new HashSet<DependencyGraph.TransitiveEntry>();
		public final Set<Artifact> excludes = new HashSet<Artifact>();
		public DependencyGraph graph;
	}
	
	public static class TransitiveEntry {
		public TransitiveEntry(Artifact artifact, String systemPath) {
			this.artifact = artifact;
			this.systemPath = systemPath;
		}
		
		public final Artifact artifact;
		public final String systemPath;
	}
	
	/* the list of repositories available to resolve the transitive dependencies this task */
	protected List<Repository> repositories;
	/* the repository which was used to resolve the POM describing this graph */
	protected Repository resolutionRepository;
	/* the transitive dependency artifacts of this graph, sorted by their GAV (group, artifact and version) */
	protected Map<Artifact, TransitiveGroup> transitives;
	
	public DependencyGraph() {
		this(Collections.emptyList(), Collections.emptyList());
	}
	
	public DependencyGraph(List<Repository> repositories, List<TransitiveGroup> transitives) {
		this.repositories = new ArrayList<Repository>();
		this.repositories.addAll(repositories);
		this.transitives = new HashMap<Artifact, TransitiveGroup>();
		for (var g : transitives) {
			for (var a : g.artifacts) {
				addTransitive(a.artifact, g.excludes, a.systemPath);
			}
		}
		this.transitives = new HashMap<>();
	}
	
	public void addRepository(Repository repository) {
		this.repositories.add(repository);
	}
	
	public void setResolutionRepository(Repository resolutionRepository) {
		this.resolutionRepository = resolutionRepository;
		this.repositories.add(resolutionRepository);
	}
	
	public void addTransitive(Artifact artifact, Set<Artifact> excludes, String systemPath) {
		Artifact group = artifact.getGAV();
		if (!this.transitives.containsKey(group))
			this.transitives.put(group, new TransitiveGroup());
		this.transitives.get(group).artifacts.add(new TransitiveEntry(artifact, systemPath));
		if (excludes != null)
			this.transitives.get(group).excludes.addAll(excludes);
	}

	public void setTransitiveGraph(Artifact artifact, DependencyGraph graph) {
		Artifact group = artifact.getGAV();
		if (this.transitives.containsKey(group)) {
			this.transitives.get(group).graph = graph;
		}
	}
	
	public void setArtifactSystemPath(Artifact artifact, String systemPath) {
		
				
	}
	
	public List<Repository> getRepositories() {
		return this.repositories;
	}

	public Repository getResolutionRepository() {
		return resolutionRepository;
	}
	
	public Collection<Artifact> getTransitiveGroups() {
		return this.transitives.keySet();
	}
	
	public Collection<Artifact> getArtifacts(Artifact group) {
		if (group.hasGAVCE()) group = group.getGAV();
		if (!this.transitives.containsKey(group)) return Collections.emptyList();
		return this.transitives.get(group).artifacts.stream().map(t -> t.artifact).toList();
	}

	public boolean isSystemOnly(Artifact group) {
		if (!this.transitives.containsKey(group)) return false;
		return this.transitives.get(group).artifacts.stream().filter(t -> t.systemPath != null).count() == this.transitives.get(group).artifacts.size();
	}
	
	public String getSystemPath(Artifact artifact) {
		Artifact group = artifact.getGAV();
		if (!this.transitives.containsKey(group)) return null;
		for (TransitiveEntry e : this.transitives.get(group).artifacts) {
			if (e.artifact.equals(artifact)) return e.systemPath;
		}
		return null;
	}
	
	public Collection<DependencyGraph> getTransitiveGraphs() {
		return this.transitives.values().stream().map(t -> t.graph).toList();
	}

	public DependencyGraph getTransitiveGraph(Artifact group) {
		if (group.hasGAVCE()) group = group.getGAV();
		if (!this.transitives.containsKey(group)) return null;
		return this.transitives.get(group).graph;
	}
	
	public Predicate<Artifact> getExclusionPredicate(Artifact group) {
		if (group.hasGAVCE()) group = group.getGAV();
		if (!this.transitives.containsKey(group)) return a -> false;
		Collection<Artifact> excludes = this.transitives.get(group).excludes;
		return excludes == null ? a -> true : a -> excludes.stream().filter(e -> {
			return	(e.groupId.equals("*") || e.groupId.equals(a.groupId)) &&
					(e.artifactId.equals("*") || e.artifactId.equals(a.artifactId));
		}).count() == 0;
	}
	
}
