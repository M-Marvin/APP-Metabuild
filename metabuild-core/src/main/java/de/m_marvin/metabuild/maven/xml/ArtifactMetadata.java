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
public class ArtifactMetadata {
	
	@XMLField(FieldType.ELEMENT)
	public String groupId;
	@XMLField(FieldType.ELEMENT)
	public String artifactId;

	public Artifact ga() throws MavenException {
		return new Artifact(this.groupId, this.artifactId);
	}
	
	public void ga(Artifact coordinates) {
		this.groupId = coordinates.groupId;
		this.artifactId = coordinates.artifactId;
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
		public static class Versions { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = String.class) public List<String> version = new ArrayList<>(); }
		@XMLField(FieldType.ELEMENT)
		public Versions versions;
		
		/* last updated timestamp */
		
		@XMLField(FieldType.ELEMENT)
		public String lastUpdated;
		
	}

	/* POM serialization and de-serialization */

	public static final XMLUnmarshaler UNMARSHALER = new XMLUnmarshaler(true, ArtifactMetadata.class);
	public static final XMLMarshaler MARSHALER = new XMLMarshaler(true, ArtifactMetadata.class);
	
	public static ArtifactMetadata fromXML(InputStream xmlStream) throws MavenException {
		try {
			return UNMARSHALER.unmarshall(new XMLInputStream(xmlStream), ArtifactMetadata.class);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read meta version XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read meta version XML because of XML exception");
		}
	}

	public static void toXML(ArtifactMetadata metadata, OutputStream xmlStream) throws MavenException {
		try {
			MARSHALER.marshal(new XMLOutputStream(xmlStream), metadata);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read meta version XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read meta version XML because of XML exception");
		}
	}
	
}
