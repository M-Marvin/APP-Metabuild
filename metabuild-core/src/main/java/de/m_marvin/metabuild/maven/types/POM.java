package de.m_marvin.metabuild.maven.types;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "project", namespace = "http://maven.apache.org/POM/4.0.0")
public class POM {
	
	public static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";
	
//	@XmlElement(namespace = MAVEN_NAMESPACE)
//	public String modelVersion;

	/* artifact coordinates of this POM file */
	
	@XmlElement(namespace = MAVEN_NAMESPACE)
	public String groupId;
	@XmlElement(namespace = MAVEN_NAMESPACE)
	public String artifactId;
	@XmlElement(namespace = MAVEN_NAMESPACE)
	public String version;

	public Artifact gavce() throws MavenException {
		return new Artifact(fillPoperties(this.groupId), fillPoperties(this.artifactId), fillPoperties(this.version));
	}
	
	/* transitive dependency declarations (including POM imports) */
	
	@XmlElement(name = "dependency", namespace = MAVEN_NAMESPACE)
	@XmlElementWrapper(name = "dependencies", namespace = MAVEN_NAMESPACE)
	public List<Dependency> dependencies;
	@XmlElement(name = "dependency", namespace = MAVEN_NAMESPACE)
	@XmlElementWrapper(name = "dependencyManagement", namespace = MAVEN_NAMESPACE)
	public List<Dependency> dependencyManagement;

	public static class Dependency {
		
		/* artifact coordinates of the dependency */
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String groupId;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String artifactId;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String version;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String classifier;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String type;

		public Artifact gavce(POM pom) throws MavenException {
			return new Artifact(this.groupId, this.artifactId, this.version, this.classifier, this.type);
		}
		
		/* scope of the dependency, and path to look for SYSTEM scope dependencies */
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public Scope scope;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String systemPath;

		@XmlType
		@XmlEnum(String.class)
		public static enum Scope {
			COMPILE,
			PROVIDED,
			RUNTIME,
			TEST,
			SYSTEM,
			IMPORT
		}
		
		/* if this dependency is optional */
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public boolean optional;
		
		/* transitive dependencies to exclude */
		
		@XmlElement(name = "exclusion", namespace = MAVEN_NAMESPACE)
		@XmlElementWrapper(name = "exclusions", namespace = MAVEN_NAMESPACE)
		public List<Exclusion> exclusions;

		public static class Exclusion {
			
			@XmlElement(namespace = MAVEN_NAMESPACE)
			public String groupId;
			@XmlElement(namespace = MAVEN_NAMESPACE)
			public String artifactId;

			public Artifact gavce(POM pom) throws MavenException {
				return new Artifact(pom.fillPoperties(this.groupId), pom.fillPoperties(this.artifactId));
			}
			
		}
		
	}
	
	/* additional repositories for resolving of transitive dependencies */
	
	@XmlElement(name = "repository", namespace = MAVEN_NAMESPACE)
	@XmlElementWrapper(name = "repositories", namespace = MAVEN_NAMESPACE)
	public List<Repository> repositories;

	public static class Repository {
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String id;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String name;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String url;
		
	}
	
	/* parent POM to import dependencies and repositories from */
	
	@XmlElement(namespace = MAVEN_NAMESPACE)
	public Parent parent;

	@XmlType
	public static class Parent {
		
		/* artifact id of POM to import */
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String groupId;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String artifactId;
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String version;

		public Artifact gavce(POM pom) throws MavenException {
			return new Artifact(pom.fillPoperties(this.groupId), pom.fillPoperties(this.artifactId), pom.fillPoperties(this.version), "", "pom");
		}
		
		/* optional relative path to look for the POM, before resolving using coordinates */
		
		@XmlElement(namespace = MAVEN_NAMESPACE)
		public String relativePath;
		
	}
	
	/* key value pairs to replace in all other property strings in this POM */
	
	@XmlAnyElement
	@XmlElementWrapper(name = "properties", namespace = MAVEN_NAMESPACE)
	@XmlJavaTypeAdapter(PropertyMapAdapter.class)
	public List<Property> properties;
	
	public static class Property {
		
		public static final Property FALLBACK_PROPERTY = new Property();
		
		public String key;
		public String value = "";
		
	}
	
	protected static final Pattern PROP_PATTERN = Pattern.compile("\\$\\{([^\\$\\{\\}]+)\\}");
	
	public String fillPoperties(String str) {
		// TODO
		Matcher m = PROP_PATTERN.matcher(str);
		return m.replaceAll(r -> this.properties.stream().filter(p -> p.key.equals(r.group(1))).findAny().orElse(Property.FALLBACK_PROPERTY).value);
	}
	
	/* POM serialization and deserialiuzation */
	
	public void importPOM(POM other, boolean fullImport) {
		
		if (fullImport) {
			
			if (other.repositories != null) {
				if (this.repositories == null) this.repositories = new ArrayList<POM.Repository>();
				this.repositories.addAll(other.repositories);
			}
			
			if (other.dependencies != null) {
				if (this.dependencies == null) this.dependencies = new ArrayList<POM.Dependency>();
				this.dependencies.addAll(other.dependencies);
			}
			
			if (other.properties != null) {
				if (this.properties == null) this.properties = new ArrayList<POM.Property>();
				this.properties.addAll(other.properties);
			}
			
		}
		
		if (other.dependencyManagement != null) {
			if (this.dependencyManagement == null) this.dependencyManagement = new ArrayList<POM.Dependency>();
			this.dependencyManagement.addAll(other.dependencyManagement);
		}
		
	}
	
	public static class PropertyMapAdapter extends XmlAdapter<Element, Property> {

		@Override
		public Property unmarshal(Element v) throws Exception {
			Property p = new Property();
			p.key = v.getNodeName();
			p.value = v.getFirstChild().getNodeValue();
			return p;
		}

		@Override
		public Element marshal(Property v) throws Exception {
			
			// FIXME
			Document d = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
			Element e = d.createElement(v.key);
			e.appendChild(d.createTextNode(v.value));
			
			return e;
		}
		
	}
	
	public static POM fromXML(InputStream xmlStream) throws MavenException {
		try {
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
			XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(xmlStream);
			JAXBContext context = JAXBContext.newInstance(POM.class);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			return unmarshaller.unmarshal(xmlReader, POM.class).getValue();
		} catch (JAXBException | XMLStreamException e) {
			throw new MavenException(e, "unable to parse POM XML");
		}
	}
	
}
