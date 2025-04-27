package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.m_marvin.metabuild.maven.handler.MavenResolver;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.MavenException;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.Credentials;
import de.m_marvin.simplelogging.Log;

public class Test {
	
	public static void main(String... args) throws MavenException, FileNotFoundException, MalformedURLException, URISyntaxException {
		
		File local = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getFile(), "../../temp");
		
		System.out.println(local);
		
		DependencyGraph graph = new DependencyGraph();
		graph.addTransitive(Artifact.of("javax.xml.bind:jaxb-api:2.2.4"), null, null);
		graph.addTransitive(Artifact.of("javax.xml.bind:jaxba-api:sources:2.2.4"), null, null);
		graph.addTransitive(Artifact.of("javax.xml.bind:jaxb-api:javadoc:2.2.4"), null, null);
		graph.addTransitive(Artifact.of("de.m_marvin.reposerver:reposervertest:0.1.1-SNAPSHOT"), null, null);
		graph.addRepository(new Repository("Local Repo", new URL("http://192.168.178.21/maven")));
		graph.addRepository(new Repository("Maven Central", new URL("https://repo.maven.apache.org/maven2")));
		graph.addRepository(new Repository("GitHub Packages", new URL("https://maven.pkg.github.com/m-marvin/app-httpserver"),
				new Credentials(
						() -> System.getenv("GITHUB_ACTOR"),
						() -> System.getenv("GITHUB_TOKEN")
				)));
		graph.addRepository(new Repository("Maven Local", new URL("file:///C:/Users/marvi/.m2/repository")));
		
		List<File> artifacts = new ArrayList<File>();
		MavenResolver resolver = new MavenResolver(Log.defaultLogger(), local);
//		resolver.setRefreshLocal(true);
		resolver.resolveGraph(graph, artifacts, r -> true);
		
		for (File f : artifacts) {
			System.out.println("-> " + f);
		}
		
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
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		
		
		
	}
	
}
