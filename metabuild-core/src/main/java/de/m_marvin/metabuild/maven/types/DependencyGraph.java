package de.m_marvin.metabuild.maven.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DependencyGraph {
	
	/* the list of repositories available to resolve the transitive dependencies this task */
	protected List<Repository> repositories;
	/* the repository which was used to resolve the POM describing this graph */
	protected Repository resolutionRepository;
	/* the transitive artifacts of this graph */
	protected Map<Artifact, Set<Artifact>> artifacts;
	/* the graphs of the individual transitive dependencies */
	protected Map<Integer, DependencyGraph> transitives;
	
	public DependencyGraph() {
		this(new ArrayList<Repository>(), new ArrayList<Artifact>());
	}
	
	public DependencyGraph(List<Repository> repositories, List<Artifact> artifacts) {
		this.repositories = repositories;
		this.artifacts = new HashMap<Artifact, Set<Artifact>>();
		for (var a : artifacts) this.artifacts.put(a, null);
		this.transitives = new HashMap<>();
	}
	
	public void addRepository(Repository repository) {
		this.repositories.add(repository);
	}
	
	public void setResolutionRepository(Repository resolutionRepository) {
		this.resolutionRepository = resolutionRepository;
		this.repositories.add(resolutionRepository);
	}
	
	public void addArtifact(Artifact artifact, Set<Artifact> transitiveExcludes) {
		this.artifacts.put(artifact, transitiveExcludes);
	}

	public void setTransitiveGraph(Artifact artifact, DependencyGraph transitives) {
		this.transitives.put(artifact.groupHash(), transitives);
	}
	
	public void setArtifactSystemPath(Artifact artifact, String fillPoperties) {
		// TODO Auto-generated method stub
		
	}
	
	public List<Repository> getRepositories() {
		return this.repositories;
	}

	public Repository getResolutionRepository() {
		return resolutionRepository;
	}
	
	public Collection<Artifact> getArtifacts() {
		return this.artifacts.keySet();
	}

	public Collection<DependencyGraph> getTransitiveGraphs() {
		return this.transitives.values();
	}

	public DependencyGraph getTransitiveGraph(Artifact transitive) {
		return this.transitives.get(transitive.groupHash());
	}
	
	public Predicate<Artifact> getExclusionPredicate(Artifact transitive) {
		Collection<Artifact> excludes = this.artifacts.get(transitive);
		return excludes == null ? a -> true : a -> excludes.stream().filter(e -> e.groupHash() != a.groupHash()).count() == 0;
	}
	
}
