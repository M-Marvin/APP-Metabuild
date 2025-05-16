package de.m_marvin.metabuild.maven.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.Artifact.DataLevel;
import de.m_marvin.metabuild.maven.types.DependencyGraph;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveEntry;
import de.m_marvin.metabuild.maven.types.DependencyGraph.TransitiveGroup;
import de.m_marvin.metabuild.maven.types.PublishConfiguration;
import de.m_marvin.metabuild.maven.types.Repository;
import de.m_marvin.metabuild.maven.types.Repository.ArtifactFile;
import de.m_marvin.metabuild.maven.xml.MetaVersion;
import de.m_marvin.metabuild.maven.xml.POM;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.TagLogger;

public class MavenPublisher {

	protected final Logger logger;
	private TimeUnit remoteTimeoutUnit = TimeUnit.SECONDS;
	private long remoteTimeout = 5;
	private Consumer<String> statusCallback = s -> {};
	private final MavenResolver resolver;
	
	public static enum ResolutionStrategy {
		OFFLINE,REMOTE,FORCE_REMOTE;
	}
	
	public MavenPublisher(Logger logger, MavenResolver resolver) {
		this.logger = new TagLogger(logger, "resolver");
		this.resolver = resolver;
	}
	
	public void setStatusCallback(Consumer<String> statusCallback) {
		this.statusCallback = statusCallback;
	}
	
	/**
	 * Sets the timeout for remote connections
	 */
	public void setRemoteTimeout(long timeout, TimeUnit unit) {
		this.remoteTimeout = timeout;
		this.remoteTimeoutUnit = unit;
	}
	
	protected Logger logger() {
		return this.logger;
	}
	
	public boolean publishConfiguration(PublishConfiguration config, ZonedDateTime timeOfCreation) throws MavenException {
		
		// Fill POM structure
		POM pom = makePOM(config.coordinates, config.dependencies);
		
		// Attempt upload on all repositories
		boolean failure = false;
		for (Repository repository : config.repositories) {
			
			if (!uploadArtifacts(repository, config.artifacts, pom, timeOfCreation))
				failure = true;
			
		}
		
		return failure;
		
	}
	
	public static final String TIMESTAMP_FORMAT = "%04d%02d%02d-%02d%02d%02d-%d";
	
	public boolean uploadArtifacts(Repository repository, Map<String, File> artifacts, POM pom, ZonedDateTime timeOfCreation) throws MavenException {
		
		Artifact pomArtifact = pom.gavce().getPOMId();
		
		// TODO SNAPSHOT RESOLUTION
		
		if (pomArtifact.isSnapshot()) {
			
			int buildNumber = 1;
			try {
				File snapshotMeta = this.resolver.downloadArtifact(repository, pomArtifact, DataLevel.META_VERSION);
				if (snapshotMeta != null) {
					MetaVersion versionMetadata = MetaVersion.fromXML(new FileInputStream(snapshotMeta));
					buildNumber = versionMetadata.versioning.snapshot.buildNumber + 1;
				}
			} catch (FileNotFoundException | MavenException e) {
				throw new MavenException(e, "exception while requesting version level meta data for snapshot upload: " + pomArtifact);
			}
			
			String timestamp = String.format(TIMESTAMP_FORMAT, 
					timeOfCreation.getYear(), 
					timeOfCreation.getMonthValue(), 
					timeOfCreation.getDayOfMonth(), 
					timeOfCreation.getHour(), 
					timeOfCreation.getMinute(), 
					timeOfCreation.getSecond(), 
					buildNumber);
			
			pomArtifact = pomArtifact.withSnapshotVersion(timestamp);
			
		}
		
		System.out.println("upload POM: " + pomArtifact);
		
		URL url = repository.artifactURL(pomArtifact, DataLevel.ARTIFACT, ArtifactFile.DATA);
		
		System.out.println("upload URL: " + url);
		
		for (String classifier : artifacts.keySet()) {
			File artifactFile = artifacts.get(classifier);
			String extenstion = FileUtility.getExtension(artifactFile);
			Artifact clsArtifact = pomArtifact.withClassifier(classifier, extenstion);
			
			System.out.println("upload ART: " + clsArtifact);
			
			URL clsurl = repository.artifactURL(clsArtifact, DataLevel.ARTIFACT, ArtifactFile.DATA);
			
			System.out.println("upload URL: " + clsurl);
			
		}
		
		return true;
		
	}
	
	public POM makePOM(Artifact coordinates, DependencyGraph graph) throws MavenException {
		
		
		POM pom = new POM();
		
		// Set POM coordinates
		pom.gavce(coordinates);
		
		// Set dependencies and repositories
		pom.dependencies = pom.new Dependencies();
		pom.repositories = pom.new Repositories();
		for (TransitiveGroup tg : graph.getTransitiveGroups()) {
			for (TransitiveEntry te : tg.artifacts) {
				
				// Add dependency
				POM.Dependency dependency = pom.new Dependency();
				dependency.gavce(te.artifact);
				dependency.scope = tg.scope;
				dependency.systemPath = te.systemPath;
				dependency.optional = te.optional;
				if (!tg.excludes.isEmpty()) {
					dependency.exclusions = dependency.new Exclusions();
					for (Artifact a : tg.excludes) {
						POM.Dependency.Exclusion exclusion = dependency.new Exclusion();
						exclusion.ga(a);
						dependency.exclusions.exclusion.add(exclusion);
					}
				}
				
				// Add repository
				Repository r = tg.graph.getResolutionRepository();
				if (r == null)
					throw new MavenException("publishing graph not resolved: %s", coordinates);
				POM.Repository repository = pom.new Repository();
				repository.id = r.name.toLowerCase().replace(' ' , '-'); // TODO
				repository.name = r.name;
				repository.url = r.baseURL.toString();
				pom.repositories.repository.add(repository);
				
			}
		}
		
		return pom;
		
	}
	
}
