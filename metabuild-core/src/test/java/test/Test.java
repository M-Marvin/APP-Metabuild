package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;

import de.m_marvin.metabuild.maven.Artifact;
import de.m_marvin.metabuild.maven.DependencyGraph;

//import de.m_marvin.metabuild.maven.Artifact;
//import de.m_marvin.metabuild.maven.DependencyGraph;

//import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
//import org.eclipse.aether.DefaultRepositorySystemSession;
//import org.eclipse.aether.RepositorySystem;
//import org.eclipse.aether.artifact.Artifact;
//import org.eclipse.aether.artifact.DefaultArtifact;
//import org.eclipse.aether.repository.LocalRepository;
//import org.eclipse.aether.repository.RemoteRepository;
//import org.eclipse.aether.resolution.ArtifactDescriptorException;
//import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
//import org.eclipse.aether.resolution.ArtifactDescriptorResult;
//import org.eclipse.aether.resolution.DependencyRequest;
//import org.eclipse.aether.supplier.RepositorySystemSupplier;

import de.m_marvin.metabuild.maven.MavenException;
import de.m_marvin.metabuild.maven.MavenResolver;
import de.m_marvin.metabuild.maven.Repository;
import de.m_marvin.simplelogging.Log;

public class Test {
	
	public static void main(String... args) throws MavenException, FileNotFoundException, MalformedURLException {
		
		DependencyGraph graph = new DependencyGraph();
		graph.addArtifact(Artifact.of("javax.xml.bind:jaxb-api:2.2.4"), null);
		graph.addArtifact(Artifact.of("javax.xml.bind:jaxb-api:sources:2.2.4"), null);
		graph.addArtifact(Artifact.of("javax.xml.bind:jaxb-api:javadoc:2.2.4"), null);
		graph.addRepository(new Repository("Maven Central", new URL("https://repo.maven.apache.org/maven2")));
		
		MavenResolver resolver = new MavenResolver(Log.defaultLogger(), new File("E:/GitHub/APP-Metabuild/temp"));
		resolver.resolveGraph(graph, true, r -> true);
		
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
