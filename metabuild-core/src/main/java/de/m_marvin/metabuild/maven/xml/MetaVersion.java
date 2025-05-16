package de.m_marvin.metabuild.maven.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.marshalling.XMLMarshaler;
import de.m_marvin.basicxml.marshalling.XMLMarshalingException;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact;

@XMLType
public class MetaVersion {
	
	/* artifact coordinates this meta data belongs too */
	
	@XMLField(FieldType.ELEMENT)
	public String groupId;
	@XMLField(FieldType.ELEMENT)
	public String artifactId;
	@XMLField(FieldType.ELEMENT)
	public String version;
	
	public Artifact gav() throws MavenException {
		return new Artifact(this.groupId, this.artifactId, this.version);
	}
	
	@XMLField(FieldType.ELEMENT)
	public Versioning versioning;
	
	@XMLType
	public static class Versioning  {
		
		/* artifact level meta data */
		
		@XMLField(FieldType.ELEMENT)
		public String latest;
		
		@XMLField(FieldType.ELEMENT)
		public String release;
		
		@XMLType
		public class Versions { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = String.class) public List<String> version = new ArrayList<>(); }
		@XMLField(FieldType.ELEMENT)
		public Versions versions;
		
		/* version level meta data */
		
		@XMLField(FieldType.ELEMENT)
		public Snapshot snapshot;
		
		@XMLType
		public static class Snapshot {
			
			@XMLField(FieldType.ELEMENT)
			public String timestamp;
			
			@XMLField(FieldType.ELEMENT)
			public int buildNumber;
			
		}
		
		@XMLType
		public class SnapshotVersions { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = SnapshotVersion.class) public List<SnapshotVersion> snapshotVersion = new ArrayList<>(); }
		@XMLField(FieldType.ELEMENT)
		public SnapshotVersions snapshotVersions;
		
		@XMLType
		public static class SnapshotVersion {
			
			@XMLField(FieldType.ELEMENT)
			public String classifier;
			
			@XMLField(FieldType.ELEMENT)
			public String extension;
			
			@XMLField(FieldType.ELEMENT)
			public String value;
			
			@XMLField(FieldType.ELEMENT)
			public String updated;
			
		}
		
		/* last updated timestamp */
		
		@XMLField(FieldType.ELEMENT)
		public String lastUpdated;
		
	}

	/* POM serialization and de-serialization */

	public static final XMLMarshaler MARSHALER = new XMLMarshaler(true, MetaVersion.class);
	
	public static MetaVersion fromXML(InputStream xmlStream) throws MavenException {
		try {
			return MARSHALER.unmarshall(new XMLInputStream(xmlStream), MetaVersion.class);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read meta version XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read meta version XML because of XML exception");
		}
	}
	
}


//@XmlRootElement(name = "metadata")
//public class MetaVersion {
//	
//	/* artifact coordinates this meta data belongs too */
//	
//	@XMLField(FieldType.ELEMENT)
//	public String groupId;
//	@XMLField(FieldType.ELEMENT)
//	public String artifactId;
//	@XMLField(FieldType.ELEMENT)
//	public String version;
//	
//	public Artifact gav() throws MavenException {
//		return new Artifact(this.groupId, this.artifactId, this.version);
//	}
//	
//	@XMLField(FieldType.ELEMENT)
//	public Versioning versioning;
//	
//	public static class Versioning  {
//		
//		/* artifact level meta data */
//		
//		@XMLField(FieldType.ELEMENT)
//		public String latest;
//		
//		@XMLField(FieldType.ELEMENT)
//		public String release;
//		
//		@XMLField(FieldType.ELEMENT)Wrapper(name = "versions")
//		@XMLField(FieldType.ELEMENT)(name = "version")
//		public List<String> versions;
//		
//		/* version level meta data */
//		
//		@XMLField(FieldType.ELEMENT)
//		public Snapshot snapshot;
//		
//		public static class Snapshot {
//			
//			@XMLField(FieldType.ELEMENT)
//			public String timestamp;
//			
//			@XMLField(FieldType.ELEMENT)
//			public String buildNumber;
//			
//		}
//		
//		@XMLField(FieldType.ELEMENT)Wrapper(name = "snapshotVersions")
//		@XMLField(FieldType.ELEMENT)(name = "snapshotVersion")
//		public List<SnapshotVersion> snapshotVersions;
//		
//		public static class SnapshotVersion {
//			
//			@XMLField(FieldType.ELEMENT)
//			public String classifier;
//			
//			@XMLField(FieldType.ELEMENT)
//			public String extension;
//			
//			@XMLField(FieldType.ELEMENT)
//			public String value;
//			
//			@XMLField(FieldType.ELEMENT)
//			public String updated;
//			
//		}
//		
//		/* last updated timestamp */
//		
//		@XMLField(FieldType.ELEMENT)
//		public String lastUpdated;
//		
//	}
//
//	/* POM serialization and de-serialization */
//
//	public static MetaVersion fromXML(InputStream xmlStream) throws MavenException {
//		try {
//			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
//			inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
//			XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(xmlStream);
//			JAXBContext context = JAXBContext.newInstance(MetaVersion.class);
//			Unmarshaller unmarshaller = context.createUnmarshaller();
//			return unmarshaller.unmarshal(xmlReader, MetaVersion.class).getValue();
//		} catch (JAXBException | XMLStreamException e) {
//			throw new MavenException(e, "unable to parse mave-metadata XML");
//		}
//	}
//	
//}
