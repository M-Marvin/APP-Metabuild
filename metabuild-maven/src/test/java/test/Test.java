package test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.m_marvin.metabuild.maven.Maven;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.handler.MavenResolver;
import de.m_marvin.metabuild.maven.handler.MavenResolver.ResolutionStrategy;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.DependencyScope;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.Credentials;
import de.m_marvin.metabuild.maven.xml.POM.Dependency.Scope;
import de.m_marvin.simplelogging.Log;

public class Test {
	
	@SuppressWarnings("deprecation")
	public static void main(String... args) throws MavenException, URISyntaxException, IOException {
		
//		URL url = URI.create("https://maven.pkg.github.com/m-marvin/library-serialportaccess/de/m_marvin/serialportaccess/jserialportaccess/maven-matedata.xml").toURL();
//		
//		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
//		if (connection instanceof HttpURLConnection httpConnection)
//			httpConnection.setRequestMethod("GET");
//		
//		connection.setRequestProperty("Connection", "Keep-Alive");
//		
//		Credentials credentials = new Credentials(
//				() -> System.getenv("GITHUB_ACTOR"), 
//				() -> System.getenv("GITHUB_TOKEN")
//		);
//		
//		// apply credentials if available
//		if (credentials != null) {
//			if (credentials.token() != null)
//				connection.setRequestProperty("Authorization", "Bearer " + credentials.bearer());
//			if (connection instanceof HttpsURLConnection httpsConnection && credentials.username() != null && credentials.password() != null)
//				httpsConnection.setAuthenticator(credentials.authenticator());
//		}
//		
//		System.out.println("STATUS CODE: " + connection.getResponseCode());
//		
//		String t = new String(connection.getInputStream().readAllBytes());
//		System.out.println(t);
//		
//		connection.getInputStream().close();
//		connection.disconnect();
		
//		connection.con
		
//		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
//		if (connection instanceof HttpURLConnection httpConnection) {
//			httpConnection.setRequestMethod("PUT");
//			httpConnection.setRequestProperty("User-Agent", "Gradle/8.9 (Windows 11;10.0;amd64) (Microsoft;21.0.3;21.0.3+9-LTS)");
//		}
//		
//		Credentials credentials = new Credentials(
//				() -> System.getenv("GITHUB_ACTOR"), 
//				() -> System.getenv("GITHUB_TOKEN")
//		);
		
//		// apply credentials if available
//		if (credentials != null) {
//			if (credentials.token() != null)
//				connection.setRequestProperty("Authorization", "Bearer " + credentials.bearer());
//			if (connection instanceof HttpsURLConnection httpsConnection && credentials.username() != null && credentials.password() != null)
//				httpsConnection.setAuthenticator(credentials.authenticator());
//		}
//		connection.setRequestMethod("PUT");
//		connection.setDoOutput(true);
//		
//		connection.getOutputStream().write("<metadata><groupId>de.m_marvin.serialportaccess</groupId><artifactId>serialportaccess-linarm32</artifactId><versioning><latest>1.0</latest><release>1.0</release><versions><version>1.0</version></versions><lastUpdated>20250614-205700</lastUpdated></versioning></metadata>".getBytes());
//		
//		connection.getOutputStream().close();
//		
//		System.out.println("STATUS CODE: " + connection.getResponseCode());
//		
//		connection.disconnect();
//			
//		System.exit(-1);
		
		File local = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getFile(), "../../temp");
		
		System.out.println(local);
		
		DependencyGraph graph = new DependencyGraph();
//		graph.addTransitive(Scope.COMPILE, Artifact.of("javax.xml.bind:jaxb-api:2.2.4"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("javax.xml.bind:jaxb-api:sources:2.2.4"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("javax.xml.bind:jaxb-api:javadoc:2.2.4"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.reposerver:reposervertest:0.1.0"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.metabuild:metabuild-core:0.1-SNAPSHOT"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.simplelogging:simplelogging:sources:2.3"), null, null, false);

//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.commandlineparser:commandlineutility:2.0"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.simplelogging:simplelogging:2.3.1"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.javarun:javarun:1.2"), null, null, false);
//		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.basicxml:basicxml:1.1"), null, null, false);
		graph.addTransitive(Scope.COMPILE, Artifact.of("de.m_marvin.openui:openui:1.3.2.1-alpha"), null, null, false);
		
		graph.addRepository(Maven.mavenCentral());
		graph.addRepository(new Repository(
				"GHP SimpleLogging",
				"https://maven.pkg.github.com/m-marvin/library-simplelogging",
				new Credentials(
						() -> System.getenv("GITHUB_ACTOR"), 
						() -> System.getenv("GITHUB_TOKEN")
				)
		));
		
//		dependencies.implementation("de.m_marvin.commandlineparser:commandlineutility:2.0");
//		dependencies.implementation("de.m_marvin.commandlineparser:commandlineutility:sources:2.0");
//		
//		// SimpleLogging
//		dependencies.implementation("de.m_marvin.simplelogging:simplelogging:2.3.1");
//		dependencies.implementation("de.m_marvin.simplelogging:simplelogging:sources:2.3.1");
//		
//		// JavaRun
//		dependencies.implementation("de.m_marvin.javarun:javarun:1.2");
//		dependencies.implementation("de.m_marvin.javarun:javarun:sources:1.2");
//		
//		// BasicXML
//		dependencies.implementation("de.m_marvin.basicxml:basicxml:1.1");
//		dependencies.implementation("de.m_marvin.basicxml:basicxml:sources:1.1");
		
		
//		dependencies.implementation("de.m_marvin.commandlineparser:commandlineutility:2.0");
//		dependencies.implementation("de.m_marvin.commandlineparser:commandlineutility:sources:2.0");
//		
//		// SimpleLogging
//		dependencies.implementation("de.m_marvin.simplelogging:simplelogging:2.3");
//		dependencies.implementation("de.m_marvin.simplelogging:simplelogging:sources:2.3");
//		
//		// JavaRun
//		dependencies.implementation("de.m_marvin.javarun:javarun:1.2");
//		dependencies.implementation("de.m_marvin.javarun:javarun:sources:1.2");
//		
//		// BasicXML
//		dependencies.implementation("de.m_marvin.metabuild:basicxml:0.1_build1");
		
		
//		graph.addTransitive(Scope.SYSTEM, Artifact.of("local:systemfile:1.0"), null, "C:/test.txt");
//		graph.addRepository(new Repository("Local Repo", new URL("http://192.168.178.21/maven")));
//		graph.addRepository(new Repository("Maven Central", new URL("https://repo.maven.apache.org/maven2")));
//		graph.addRepository(new Repository("GitHub Packages", new URL("https://maven.pkg.github.com/m-marvin/app-httpserver"),
//				new Credentials(
//						() -> System.getenv("GITHUB_ACTOR"),
//						() -> System.getenv("GITHUB_TOKEN")
//				)));
		graph.addRepository(new Repository("Maven Local", new URL("file:///C:/Users/marvi/.m2/repository")));
		
		List<File> artifacts = new ArrayList<File>();
		Map<Artifact, Integer> effective = new HashMap<>();
		MavenResolver resolver = new MavenResolver(Log.defaultLogger(), local);
//		resolver.setResolutionStrategy(ResolutionStrategy.FORCE_REMOTE);
		boolean success = false;
		resolver.setAutoIncludeSources(true);
		resolver.setResolutionStrategy(ResolutionStrategy.OFFLINE);
		if (resolver.resolveGraph(graph, r -> false, effective, 0, DependencyScope.TEST_COMPILETIME)) {
			success = resolver.downloadArtifacts(graph, effective.keySet(), artifacts, DependencyScope.TEST_COMPILETIME);
		}

		System.out.println("=> " + success);
		for (File f : artifacts) {
			System.out.println("-> " + f);
		}
		
		System.out.println(artifacts);
		
//		PublishConfiguration publish = new PublishConfiguration();
//		publish.dependencies = graph;
//		publish.coordinates = Artifact.of("de.m_marvin.reposerver:reposervertest:0.1.1-SNAPSHOT").withSnapshotVersion("214135153");
//		publish.artifacts.put("", new File(local, "out.zip"));
//		publish.repositories.add(Maven.mavenLocal());
//		publish.timeOfCreation = Instant.now().atZone(ZoneOffset.UTC);
//		MavenPublisher publisher = new MavenPublisher(Log.defaultLogger(), resolver);
//		boolean success2 = publisher.publishConfiguration(publish);
//		
//		System.out.println("=> " + success2);
		
		
		
		
//		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
//
//		RepositorySystemSupplier supplier = new RepositorySystemSupplier();
//		
//		RepositorySystem repoSystem =  supplier.get();//locator.getService(RepositorySystem.class);
//		
//		File rf = new File("/temp");
//		System.out.println(rf.getAbsolutePath());
//		LocalRepository localRepo = new LocalRepository(rf);
//		session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));
//		
//		Artifact artifact = new DefaultArtifact("com.sun.xml.bind:jaxb-impl:3.0.0");
//		List<RemoteRepository> repos = new ArrayList<RemoteRepository>();
//		repos.add(new RemoteRepository.Builder( "central", "default", "https://repo.maven.apache.org/maven2" ).build());
//
//		ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest(artifact, repos, null);
//		
//		try {
//			ArtifactDescriptorResult result = repoSystem.readArtifactDescriptor(session, descriptorRequest);
//			
//			System.out.println(result.getDependencies());
//			
////			DependencyRequest dependencyRequest = new Depende
////			repoSystem.resolveDependencies(session, null)
//			
//		} catch (ArtifactDescriptorException e) {
//			e.printStackTrace();
//		}
		
	}
	
}
