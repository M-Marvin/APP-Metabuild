package de.m_marvin.metabuild.maven.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.marshaling.XMLMarshaler;
import de.m_marvin.basicxml.marshaling.XMLMarshalingException;
import de.m_marvin.basicxml.marshaling.XMLUnmarshaler;
import de.m_marvin.basicxml.marshaling.annotations.XMLField;
import de.m_marvin.basicxml.marshaling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshaling.annotations.XMLRootType;
import de.m_marvin.basicxml.marshaling.annotations.XMLType;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact;

@XMLType
@XMLRootType("metadata")
public class VersionMetadata {

	@XMLField(FieldType.ELEMENT)
	public String groupId;
	@XMLField(FieldType.ELEMENT)
	public String artifactId;
	@XMLField(FieldType.ELEMENT)
	public String version;
	
	public Artifact gav() throws MavenException {
		return new Artifact(this.groupId, this.artifactId, this.version);
	}
	
	public void gav(Artifact coordinates) {
		this.groupId = coordinates.groupId;
		this.artifactId = coordinates.artifactId;
		this.version = coordinates.baseVersion;
	}
	
	@XMLField(FieldType.ELEMENT)
	public Versioning versioning;
	
	@XMLType
	public static class Versioning  {
		
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
		public static class SnapshotVersions { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = SnapshotVersion.class) public List<SnapshotVersion> snapshotVersion = new ArrayList<>(); }
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

	public static final XMLUnmarshaler UNMARSHALER = new XMLUnmarshaler(true, VersionMetadata.class);
	public static final XMLMarshaler MARSHALER = new XMLMarshaler(true, VersionMetadata.class);
	
	public static VersionMetadata fromXML(InputStream xmlStream) throws MavenException {
		try {
			return UNMARSHALER.unmarshall(new XMLInputStream(xmlStream), VersionMetadata.class);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read meta version XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read meta version XML because of XML exception");
		}
	}
	
	public static void toXML(VersionMetadata metadata, OutputStream xmlStream) throws MavenException {
		try {
			MARSHALER.marshal(new XMLOutputStream(xmlStream), metadata);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read meta version XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read meta version XML because of XML exception");
		}
	}
	
}
