package de.m_marvin.metabuild.maven.xml;

import java.io.InputStream;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.MavenException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "metadata")
public class MetaVersion {
	
	/* artifact coordinates this meta data belongs too */
	
	@XmlElement
	public String groupId;
	@XmlElement
	public String artifactId;
	@XmlElement
	public String version;
	
	public Artifact gav() throws MavenException {
		return new Artifact(this.groupId, this.artifactId, this.version);
	}
	
	@XmlElement
	public Versioning versioning;
	
	public static class Versioning  {
		
		/* artifact level meta data */
		
		@XmlElement
		public String latest;
		
		@XmlElement
		public String release;
		
		@XmlElementWrapper(name = "versions")
		@XmlElement(name = "version")
		public List<String> versions;
		
		/* version level meta data */
		
		@XmlElement
		public Snapshot snapshot;
		
		public static class Snapshot {
			
			@XmlElement
			public String timestamp;
			
			@XmlElement
			public String buildNumber;
			
		}
		
		@XmlElementWrapper(name = "snapshotVersions")
		@XmlElement(name = "snapshotVersion")
		public List<SnapshotVersion> snapshotVersions;
		
		public static class SnapshotVersion {
			
			@XmlElement
			public String classifier;
			
			@XmlElement
			public String extension;
			
			@XmlElement
			public String value;
			
			@XmlElement
			public String updated;
			
		}
		
		/* last updated timestamp */
		
		@XmlElement
		public String lastUpdated;
		
	}

	/* POM serialization and de-serialization */

	public static MetaVersion fromXML(InputStream xmlStream) throws MavenException {
		try {
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
			XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(xmlStream);
			JAXBContext context = JAXBContext.newInstance(MetaVersion.class);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			return unmarshaller.unmarshal(xmlReader, MetaVersion.class).getValue();
		} catch (JAXBException | XMLStreamException e) {
			throw new MavenException(e, "unable to parse mave-metadata XML");
		}
	}
	
}
