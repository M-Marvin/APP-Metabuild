package de.m_marvin.metabuild.maven.xml;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.ImportOrderList;
import de.m_marvin.metabuild.maven.types.MavenException;
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

@XmlRootElement(name = "project")
public class POM {
	
	/* artifact coordinates of this POM file */
	
	@XmlElement()
	public String groupId; // required
	@XmlElement()
	public String artifactId; // required
	@XmlElement()
	public String version; // required

	public Artifact gavce() throws MavenException {
		return new Artifact(fillPoperties(this.groupId), fillPoperties(this.artifactId), fillPoperties(this.version));
	}
	
	/* transitive dependency declarations (including POM imports) */
	
	@XmlElement(name = "dependency")
	@XmlElementWrapper(name = "dependencies")
	public ImportOrderList<Dependency> dependencies = null; // NOTE: ORDER OF IMPORTS IN XML
	@XmlElement(name = "dependency")
	@XmlElementWrapper(name = "dependencyManagement")
	public ImportOrderList<Dependency> dependencyManagement = null; // NOTE: ORDER OF IMPORTS IN XML

	public static class Dependency {
		
		/* artifact coordinates of the dependency */
		
		@XmlElement()
		public String groupId; // required
		@XmlElement()
		public String artifactId; // required
		@XmlElement()
		public String version = null;
		@XmlElement()
		public String classifier = "";
		@XmlElement()
		public String type = "jar";

		public Artifact gavce(POM pom) throws MavenException {
			return new Artifact(this.groupId, this.artifactId, this.version, this.classifier, this.type);
		}
		
		/* scope of the dependency, and path to look for SYSTEM scope dependencies */
		
		@XmlElement()
		public Scope scope = Scope.COMPILE;
		@XmlElement()
		public String systemPath = null;

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
		
		@XmlElement()
		public boolean optional = false;
		
		/* transitive dependencies to exclude */
		
		@XmlElement(name = "exclusion")
		@XmlElementWrapper(name = "exclusions")
		public List<Exclusion> exclusions = null;

		public static class Exclusion {
			
			@XmlElement()
			public String groupId; // required
			@XmlElement()
			public String artifactId; // required

			public Artifact ga(POM pom) throws MavenException {
				return new Artifact(pom.fillPoperties(this.groupId), pom.fillPoperties(this.artifactId));
			}
			
		}
		
	}
	
	/* additional repositories for resolving of transitive dependencies */
	
	@XmlElement(name = "repository")
	@XmlElementWrapper(name = "repositories")
	public ImportOrderList<Repository> repositories; // NOTE: ORDER OF IMPORTS IN XML

	public static class Repository {
		
		@XmlElement()
		public String id; // required
		@XmlElement()
		public String name; // required
		@XmlElement()
		public String url; // required
		
	}
	
	/* parent POM to import dependencies and repositories from */
	
	@XmlElement()
	public Parent parent = null;

	@XmlType
	public static class Parent {
		
		/* artifact id of POM to import */
		
		@XmlElement()
		public String groupId; // required
		@XmlElement()
		public String artifactId; // required
		@XmlElement()
		public String version; // required

		public Artifact gavce(POM pom) throws MavenException {
			return new Artifact(pom.fillPoperties(this.groupId), pom.fillPoperties(this.artifactId), pom.fillPoperties(this.version), "", "pom");
		}
		
		/* optional relative path to look for the POM, before resolving using coordinates */
		
		@XmlElement()
		public String relativePath = null;
		
	}
	
	/* key value pairs to replace in all other property strings in this POM */
	
	@XmlAnyElement
	@XmlElementWrapper(name = "properties")
	@XmlJavaTypeAdapter(PropertyMapAdapter.class)
	public ImportOrderList<POM.Property> properties;
	
	public static class Property {
		
		public static final Property FALLBACK_PROPERTY = new Property();
		
		public String key; // required
		public String value = "NA"; // required
		
	}
	
	protected static final Pattern PROP_PATTERN = Pattern.compile("\\$\\{([^\\$\\{\\}]+)\\}");
	
	public String fillPoperties(String str) {
		// TODO settings.x and project.x properties
		Matcher m = PROP_PATTERN.matcher(str);
		return m.replaceAll(r -> {
			String property = r.group(1);
			if (property.startsWith("env.")) {
				return System.getenv(property.substring(4));
			} else if (System.getProperties().contains(property)) {
				return System.getProperty(property);
			} else {			
				return this.properties.stream().filter(p -> p.key.equals(property)).findAny().orElse(Property.FALLBACK_PROPERTY).value;
			}
			
		});
	}
	
	/* POM serialization and de-serialization */
	
	public void importPOM(POM other, boolean fullImport) {
		
		if (fullImport) {
			
			if (other.repositories != null) {
				if (this.repositories == null) this.repositories = new ImportOrderList<POM.Repository>();
				this.repositories.importList(other.repositories);
			}
			
			if (other.dependencies != null) {
				if (this.dependencies == null) this.dependencies = new ImportOrderList<POM.Dependency>();
				this.dependencies.importList(other.dependencies);
			}
			
			if (other.properties != null) {
				if (this.properties == null) this.properties = new ImportOrderList<POM.Property>();
				this.properties.importList(other.properties);
			}
			
		}
		
		if (other.dependencyManagement != null) {
			if (this.dependencyManagement == null) this.dependencyManagement = new ImportOrderList<POM.Dependency>();
			this.dependencyManagement.importList(other.dependencyManagement);
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
			
			// FIXME marshaling of properties
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
