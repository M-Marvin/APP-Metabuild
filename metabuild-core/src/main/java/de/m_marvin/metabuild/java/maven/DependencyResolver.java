package de.m_marvin.metabuild.java.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.java.maven.MavenResolver.MavenRepository;
import de.m_marvin.metabuild.java.maven.MavenResolver.POM;
import de.m_marvin.metabuild.java.maven.MavenResolver.POM.ArtifactAbs;
import de.m_marvin.metabuild.java.maven.MavenResolver.POM.Scope;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.TagLogger;

public class DependencyResolver {
	
	public static record Dependency(String dependency, String[] configurations) {}

	public static enum QueryMode {
		CACHE_ONLY,
		CACHE_AND_ONLINE,
		ONLINE_ONLY;
	}
	
	public static final String[] DEPENDENCY_DEFAULT_CONFIGURATIONS = new String[] {"", "sources", "javadoc"};

	private Logger logger;
	protected final MavenResolver resolver;
	protected final Map<Dependency, POM> dependencies = new HashMap<>();
	protected final Map<Dependency, POM> transitives = new HashMap<>();
	protected final Map<String, File> dependencyJarPaths = new HashMap<>();
	
	public DependencyResolver(File cache, Logger logger) throws Exception {
		this.logger = logger;
		this.resolver = new MavenResolver(cache, new TagLogger(logger, "/maven"));
	}
	
	public MavenResolver getResolver() {
		return resolver;
	}
	
	public Map<Dependency, POM> getDependencyPOMs() {
		return dependencies;
	}
	
	public Map<Dependency, POM> getTransitivePOMs() {
		return transitives;
	}
	
	public Map<String, File> getDependencyJarPaths() {
		return dependencyJarPaths;
	}
	
	public Logger logger() {
		return this.logger;
	}
	
	public void addRepository(MavenRepository repo) {
		this.resolver.addRepository(repo);
	}
	
	public List<MavenRepository> getRepositories() {
		return this.resolver.getRepositories();
	}
	
	public Set<Dependency> getDependencies() {
		return dependencies.keySet();
	}
	
	public void addDependency(String dependency, String... configurations) {
		if (configurations.length == 0) configurations = DEPENDENCY_DEFAULT_CONFIGURATIONS;
		if (!MavenResolver.DEPENDENCY_STRING_PATTERN.matcher(dependency).find())
			throw new IllegalArgumentException("Dependency invalid format: " + dependency);
		this.dependencies.put(new Dependency(dependency, configurations), null);
	}
	
	public void resolveDependencies(Predicate<Scope> resolveScope, Consumer<String> resolveCallback) {
		resolveDependencies(resolveScope, QueryMode.CACHE_AND_ONLINE, resolveCallback);
	}

	public void resolveDependencies(Predicate<Scope> resolveScope, QueryMode mode, Consumer<String> resolveCallback) {
		resolveDependencies(resolveScope, artifact -> mode, resolveCallback);
	}
	
	public void resolveDependencies(Predicate<Scope> resolveScope, Function<String, QueryMode> mode, Consumer<String> resolveCallback) {
		
		Collection<Dependency> deps = this.dependencies.keySet();
		
		while (deps.size() > 0) {
			
			for (Dependency dep : deps) {
				
				if (resolveCallback != null) resolveCallback.accept(dep.dependency());
				
				//logger().info("resolve dependency: '%s'", dep.dependency());
				
				Optional<POM> pom = resolveDependency(dep, mode.apply(dep.dependency()));
				
				if (pom.isEmpty())
					throw BuildException.msg("unable to find dependency: %s", dep.dependency());
				
				if (this.dependencies.containsKey(dep)) {
					this.dependencies.put(dep, pom.get());
				} else {
					this.transitives.put(dep, pom.get());
				}
				
				// Resolve imports
				Queue<ArtifactAbs> il = new ArrayDeque<>();
				il.addAll(pom.get().imports());
				while (il.size() > 0) {
					ArtifactAbs imp = il.poll();
					logger().debug("import POM: '%s:%s:%s'", imp.group(), imp.artifact(), imp.version());
					String artifactName = String.format("%s:%s:%s", imp.group(), imp.artifact(), imp.version());
					Optional<POM> tpom = resolveDependency(new Dependency(artifactName, null), mode.apply(artifactName));
					if (tpom.isEmpty())
						throw BuildException.msg("POM import could not be resolved: '%s:%s:%s'", imp.group(), imp.artifact(), imp.version());
					pom.get().importPOM(imp, tpom.get());
					il.addAll(tpom.get().imports());
				}
				
				// Add declared repositories
				for (var repository : pom.get().repositorities()) {
					this.resolver.addRepository(repository);
				}
				
				// Resolve absolute transitive dependencies
				for (var transitive : pom.get().dependenciesAbs()) {
					if (resolveScope != null && !resolveScope.test(transitive.scope())) continue;
					Dependency tdep = new Dependency(String.format("%s:%s:%s", transitive.group(), transitive.artifact(), transitive.version()), DEPENDENCY_DEFAULT_CONFIGURATIONS);
					if (!this.transitives.containsKey(tdep) && !this.dependencies.containsKey(tdep)) {
						this.transitives.put(tdep, null);
						logger().info("transitive dependency: '%s:%s:%s'", transitive.group(), transitive.artifact(), transitive.version());
					}
				}
				
				// Resolve declared transitive dependencies
				for (var transitive : pom.get().dependencies()) {
					if (resolveScope != null && !resolveScope.test(transitive.scope())) continue;
					Optional<String> versionDeclared = pom.get().declerations().stream()
							.filter(d -> d.group().equals(transitive.group()) && d.artifact().equals(transitive.artifact()))
							.map(d -> d.version())
							.findFirst();
					if (versionDeclared.isEmpty())
						throw BuildException.msg("Dependency '%s' or one of its imports has undeclared transitive '%s:%s:<undefined>'!", dep.dependency(), transitive.group(), transitive.artifact());
					Dependency tdep = new Dependency(String.format("%s:%s:%s", transitive.group(), transitive.artifact(), versionDeclared.get()), DEPENDENCY_DEFAULT_CONFIGURATIONS);
					if (!this.transitives.containsKey(tdep) && !this.dependencies.containsKey(tdep)) {
						this.transitives.put(tdep, null);
						logger().info("transitive dependency: '%s:%s:%s'", transitive.group(), transitive.artifact(), versionDeclared.get());
					}
				}
				
			}
			
			// Resolve transitive of transitive
			deps = this.transitives.entrySet().stream().filter(e -> e.getValue() == null).map(Entry::getKey).toList();
			
		}
		
	}
	
	protected Optional<POM> resolveDependency(Dependency dependency, QueryMode mode) {
		
		Matcher m = MavenResolver.DEPENDENCY_STRING_PATTERN.matcher(dependency.dependency());
		if (!m.find())
			throw BuildException.msg("Invalid dependency syntax: %s", dependency.dependency());
		String group = m.group("group");
		String artifact = m.group("artifact");
		String version = m.group("version");
		File cache = new File(this.resolver.getCache(), String.format("%s/%s/%s", group, artifact, version));
		File pomFile = new File(cache, String.format("%s-%s.pom", artifact, version));

		if (!pomFile.isFile() || mode == QueryMode.ONLINE_ONLY) {
			if (mode == QueryMode.CACHE_ONLY) return Optional.empty();
			Optional<POM> pom = this.resolver.resolve(group, artifact, version, dependency.configurations());
			if (pomFile.isFile() && pom.isPresent()) this.dependencyJarPaths.put(dependency.dependency(), cache);
			return pom;
		}

		try {
			Document doc = this.resolver.getXMLParser().parse(pomFile);
			Optional<POM> pom = this.resolver.parsePOM(doc, group, artifact, version);
			if (pom.isPresent()) this.dependencyJarPaths.put(dependency.dependency(), cache);
			return pom;
		} catch (IOException e) {
			throw BuildException.msg(e, "Failed to access dependency POM: %s", dependency.dependency());
		} catch (SAXException e) {
			throw BuildException.msg(e, "Failed to parse dependency POM: %s", dependency.dependency());
		}
		
	}
	
}
