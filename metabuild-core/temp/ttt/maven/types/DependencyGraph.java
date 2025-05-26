package de.m_marvin.metabuild.maven.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.m_marvin.metabuild.maven.xml.POM.Dependency.Scope;

public class DependencyGraph {
	
	public static class TransitiveGroup {
		public final Set<TransitiveEntry> artifacts = new HashSet<TransitiveEntry>();
		public final Set<Artifact> excludes = new HashSet<Artifact>();
		public final Scope scope;
		public final Artifact group;
		public DependencyGraph graph;
		
		public TransitiveGroup(Scope scope, Artifact group) {
			this.scope = scope;
			this.group = group;
		}
	}
	
	public static class TransitiveEntry {
		public TransitiveEntry(Artifact artifact, String systemPath, boolean optional) {
			this.artifact = artifact;
			this.systemPath = systemPath;
			this.optional = optional;
		}
		
		public final Artifact artifact;
		public final String systemPath;
		public final boolean optional;
	}
	
	/* the list of repositories available to resolve the transitive dependencies of this task */
	protected final List<Repository> repositories;
	/* the repository which was used to resolve the POM describing this graph */
	protected Repository resolutionRepository;
	/* the transitive dependency artifacts of this graph, sorted by their GAV (group, artifact and version) */
	protected final Map<Artifact, Map<Scope, TransitiveGroup>> transitives;

	public DependencyGraph() {
		this(Collections.emptyList(), Collections.emptyList());
	}
	
	public DependencyGraph(List<Repository> repositories, List<TransitiveGroup> transitives) {
		this.repositories = new ArrayList<Repository>();
		this.repositories.addAll(repositories);
		this.transitives = new HashMap<Artifact, Map<Scope, TransitiveGroup>>();
		for (var g : transitives) {
			for (var a : g.artifacts) {
				addTransitive(g.scope, a.artifact, g.excludes, a.systemPath, a.optional);
			}
		}
	}

	public DependencyGraph(DependencyGraph other) {
		this();
		Objects.requireNonNull(other);
		importFrom(other);
		this.resolutionRepository = other.resolutionRepository;
	}
	
	public void importFrom(DependencyGraph graph) {
		Objects.requireNonNull(graph);
		for (var r : graph.repositories)
			if (!this.repositories.contains(r))
				this.repositories.add(r);
		for (var g : graph.getTransitiveGroups()) {
			for (var a : g.artifacts) {
				addTransitive(g.scope, a.artifact, g.excludes, a.systemPath, a.optional);
				if (g.graph != null)
					this.transitives.get(a.artifact.getGAV()).get(g.scope).graph = new DependencyGraph(g.graph);
			}
		}
	}
	
	public void importAll(Set<DependencyGraph> dependencies) {
		Objects.requireNonNull(dependencies);
		dependencies.forEach(this::importFrom);
	}
	
	public void addRepository(Repository repository) {
		if (!this.repositories.contains(repository))
			this.repositories.remove(repository); // to allow updating of credentials
		this.repositories.add(repository);
	}
	
	public void setResolutionRepository(Repository resolutionRepository) {
		this.resolutionRepository = resolutionRepository;
		addRepository(resolutionRepository);
	}
	
	public void addTransitive(Scope scope, Artifact artifact, Set<Artifact> excludes, String systemPath, boolean optional) {
		if (!artifact.hasGAVCE()) return;
		Artifact group = artifact.getGAV();
		Map<Scope, TransitiveGroup> scopes = this.transitives.get(group);
		if (scopes == null) {
			scopes = new HashMap<Scope, TransitiveGroup>();
			this.transitives.put(group, scopes);
		}
		TransitiveGroup scopegroup = scopes.get(scope);
		if (scopegroup == null) {
			scopegroup = new TransitiveGroup(scope, group);
			scopes.put(scope, scopegroup);
		}
		scopegroup.artifacts.add(new TransitiveEntry(artifact, systemPath, optional));
		if (excludes != null)
			scopegroup.excludes.addAll(excludes);
	}

	public Collection<TransitiveGroup> getTransitiveGroups() {
		return this.transitives.values().stream().flatMap(e -> e.values().stream()).toList();
	}
	
	public List<Repository> getRepositories() {
		return this.repositories;
	}

	public Repository getResolutionRepository() {
		return resolutionRepository;
	}
	
}
