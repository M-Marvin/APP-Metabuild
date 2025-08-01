package de.m_marvin.metabuild.maven.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.marshaling.XMLMarshaler;
import de.m_marvin.basicxml.marshaling.XMLMarshalingException;
import de.m_marvin.basicxml.marshaling.XMLUnmarshaler;
import de.m_marvin.basicxml.marshaling.annotations.XMLEnum;
import de.m_marvin.basicxml.marshaling.annotations.XMLField;
import de.m_marvin.basicxml.marshaling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshaling.annotations.XMLRootType;
import de.m_marvin.basicxml.marshaling.annotations.XMLType;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact;
import de.m_marvin.metabuild.maven.types.ImportOrderList;

@XMLType
@XMLRootType(value = "project", namespace = "http://maven.apache.org/POM/4.0.0")
public class POM {
	
	public static final String NS = "http://maven.apache.org/POM/4.0.0";
	
	/* artifact coordinates of this POM file */
	
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public String groupId; // required
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public String artifactId; // required
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public String version; // required
	
	public Artifact gavce() throws MavenException {
		return new Artifact(fillPoperties(this.groupId), fillPoperties(this.artifactId), fillPoperties(this.version));
	}
	
	public void gavce(Artifact coordinates) {
		this.groupId = coordinates.groupId;
		this.artifactId = coordinates.artifactId;
		this.version = coordinates.baseVersion;
	}
	
	/* transitive dependency declarations (including POM imports) */
	
	@XMLType
	public class Dependencies { @XMLField(value = FieldType.ELEMENT_COLLECTION, namespace = NS, type = Dependency.class) public ImportOrderList<Dependency> dependency = new ImportOrderList<POM.Dependency>(); }
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public Dependencies dependencies;
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public Dependencies dependencyManagement; // NOTE: ORDER OF IMPORTS IN XML
	
	@XMLType
	public class Dependency {

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Dependency other)
				return 	Objects.equals(other.groupId, this.groupId) &&
						Objects.equals(other.artifactId, this.artifactId) &&
						Objects.equals(other.version, this.version) &&
						Objects.equals(other.classifier, this.classifier) &&
						Objects.equals(other.type, this.type);
			return false;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.groupId, this.artifactId, this.version, this.classifier, this.type);
		}
		
		/* artifact coordinates of the dependency */
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String groupId; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String artifactId; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String version = null;
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String classifier = "";
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String type = "jar";

		public Artifact gavce() throws MavenException {
			return new Artifact(this.groupId, this.artifactId, this.version, this.classifier, this.type);
		}
		
		public void gavce(Artifact coordinates) {
			this.groupId = coordinates.groupId;
			this.artifactId = coordinates.artifactId;
			this.version = coordinates.baseVersion;
			this.classifier = coordinates.classifier;
			this.type = coordinates.extension;
		}
		
		/* scope of the dependency, and path to look for SYSTEM scope dependencies */
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public Scope scope = Scope.COMPILE;
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String systemPath = null;
		
		public static enum Scope {
			@XMLEnum("compile")
			COMPILE(5),
			@XMLEnum("provided")
			PROVIDED(4),
			@XMLEnum("runtime")
			RUNTIME(2),
			@XMLEnum("test")
			TEST(1),
			@XMLEnum("system")
			SYSTEM(0),
			@XMLEnum("import")
			IMPORT(0);
			
			private final int priority;
			
			private Scope(int priority) {
				this.priority = priority;
			}
			
			public Scope priotity(Collection<Scope> scopes) {
				Scope scope = null;
				for (Scope s : scopes)
					if (scope == null || s.priority > scope.priority) scope = s;
				return scope;
			}
			
			public Scope effective(Scope transitiveScope) {
				switch (this) {
				case COMPILE: {
					switch (transitiveScope) {
					case COMPILE: return COMPILE;
					case RUNTIME: return RUNTIME;
					case SYSTEM: return SYSTEM;
					default: return null;
					}
				}
				case PROVIDED: {
					switch (transitiveScope) {
					case COMPILE: return PROVIDED;
					case RUNTIME: return PROVIDED;
					case SYSTEM: return SYSTEM;
					default: return null;
					}
				}
				case RUNTIME: {
					switch (transitiveScope) {
					case COMPILE: return RUNTIME;
					case RUNTIME: return RUNTIME;
					default: return null;
					}
				}
				case TEST: {
					switch (transitiveScope) {
					case COMPILE: return TEST;
					case RUNTIME: return TEST;
					default: return null;
					}
				}
				default: return null;
				}
			}
		}
		
		/* if this dependency is optional */
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public boolean optional = false;
		
		/* transitive dependencies to exclude */
		
		@XMLType
		public class Exclusions { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = Exclusion.class) public List<Exclusion> exclusion = new ArrayList<POM.Dependency.Exclusion>(); }
		@XMLField(value = FieldType.ELEMENT, namespace = NS) 
		public Exclusions exclusions = null;
		
		@XMLType
		public class Exclusion {
			
			@XMLField(value = FieldType.ELEMENT, namespace = NS)
			public String groupId; // required
			@XMLField(value = FieldType.ELEMENT, namespace = NS)
			public String artifactId; // required

			public Artifact ga() throws MavenException {
				return new Artifact(fillPoperties(this.groupId), fillPoperties(this.artifactId));
			}
			
			public void ga(Artifact coordinates) {
				this.groupId = coordinates.groupId;
				this.artifactId = coordinates.artifactId;
			}

			@Override
			public boolean equals(Object obj) {
				if (obj instanceof Exclusion other)
					return Objects.equals(other.groupId, this.groupId) && Objects.equals(this.artifactId, other.artifactId);
				return false;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.groupId, this.artifactId);
			}
			
		}
		
	}
	
	/* additional repositories for resolving of transitive dependencies */
	
	@XMLType
	public class Repositories { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = Repository.class) public ImportOrderList<Repository> repository = new ImportOrderList<Repository>(); }
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public Repositories repositories;// NOTE: ORDER OF IMPORTS IN XML

	@XMLType
	public class Repository {
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String id; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String name; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String url; // required
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Repository other)
				return Objects.equals(other.url, this.url);
			return false;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.url);
		}
		
	}
	
	/* parent POM to import dependencies and repositories from */
	
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public Parent parent = null;

	@XMLType
	public class Parent {
		
		/* artifact id of POM to import */
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String groupId; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String artifactId; // required
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String version; // required

		public Artifact gavce() throws MavenException {
			return new Artifact(fillPoperties(this.groupId), fillPoperties(this.artifactId), fillPoperties(this.version), "", "pom");
		}
		
		public void gavce(Artifact coordinates) {
			this.groupId = coordinates.groupId;
			this.artifactId = coordinates.artifactId;
			this.version = coordinates.baseVersion;
		}
		
		/* optional relative path to look for the POM, before resolving using coordinates */
		
		@XMLField(value = FieldType.ELEMENT, namespace = NS)
		public String relativePath = null;
		
	}
	
	/* key value pairs to replace in all other property strings in this POM */
	
	@XMLType
	public class Properties { @XMLField(value = FieldType.REMAINING_ELEMENT_MAP, namespace = NS, type = String.class) public Map<String, String> property = new HashMap<String, String>(); }
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public Properties properties;
	
	protected static final Pattern PROP_PATTERN = Pattern.compile("\\$\\{([^\\$\\{\\}]+)\\}");
	
	public String fillPoperties(String str) {
		// TODO settings.x and project.x properties
		if (str == null) return null;
		Matcher m = PROP_PATTERN.matcher(str);
		return m.replaceAll(r -> {
			String property = r.group(1);
			if (property.startsWith("env.")) {
				return System.getenv(property.substring(4)).replace("\\", "\\\\").replace("$", "\\$");
			} else if (System.getProperties().contains(property)) {
				return System.getProperty(property);
			} else {
				return this.properties.property.getOrDefault(property, "NA");
			}
		});
	}
	
	private final static POM DUMMY_POM = new POM();
	
	public static String fillPropertiesStatic(String str) {
		return DUMMY_POM.fillPoperties(str);
	}
	
	/* POM serialization and de-serialization */
	
	public void importPOM(POM other, boolean fullImport) {
		
		if (fullImport) {
			
			if (other.repositories != null) {
				if (this.repositories == null) this.repositories = new Repositories();
				this.repositories.repository.importList(other.repositories.repository);
			}
			
			if (other.dependencies != null) {
				if (this.dependencies == null) this.dependencies = new Dependencies();
				this.dependencies.dependency.importList(other.dependencies.dependency);
			}
			
			if (other.properties != null) {
				if (this.properties == null) this.properties = new Properties();
				this.properties.property.putAll(other.properties.property);
			}
			
		}
		
		if (other.dependencyManagement != null) {
			if (this.dependencyManagement == null) this.dependencyManagement = new Dependencies();
			this.dependencyManagement.dependency.importList(other.dependencyManagement.dependency);
		}
		
	}
	
	public static final XMLUnmarshaler UNMARSHALER = new XMLUnmarshaler(true, POM.class);
	public static final XMLMarshaler MARSHALER = new XMLMarshaler(false, POM.class);
	
	public static POM fromXML(InputStream xmlStream) throws MavenException {
		try {
			return UNMARSHALER.unmarshall(new XMLInputStream(xmlStream), POM.class);
		} catch (IOException e) {
			throw new MavenException(e, "unable to read POM XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to read POM XML because of XML exception");
		}
	}
	
	public static void toXML(POM pom, OutputStream xmlStream) throws MavenException {
		try {
			MARSHALER.marshal(new XMLOutputStream(xmlStream), pom);
		} catch (IOException e) {
			throw new MavenException(e, "unable to write POM XML because of IO exception");
		} catch (XMLException | XMLMarshalingException e) {
			throw new MavenException(e, "unable to write POM XML because of XML exception");
		}
	}
	
}
