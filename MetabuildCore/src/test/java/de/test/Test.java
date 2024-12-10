package de.test;

import java.io.File;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.maven.DependencyResolver;
import de.m_marvin.metabuild.maven.DependencyResolver.MavenRepository;
import de.m_marvin.metabuild.maven.DependencyResolver.MavenRepository.Credentials;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;

public class Test {
	
	public static final Pattern URL = Pattern.compile("[0-9A-Za-z$\\-_\\.+!*#'\\(\\)]+");
	public static final Pattern DEP = Pattern.compile("[0-9a-z\\-\\.]+");
	public static final String ARTIFACT_META = "maven-metadata.xml";
	
	public static DependencyResolver resolver = null;
	
	public static void main(String[] args) throws Exception {
		
		resolver = new DependencyResolver(new File("run/test"), new StacktraceLogger(new SystemLogger()));
		
		System.out.println(resolver.getCache().getAbsolutePath());
		
		
		resolver.addRepository(new MavenRepository(
				"GitHub Pkg 1", 
				"https://maven.pkg.github.com/m-marvin/library-graphicsframework", 
				new Credentials(
						() -> System.getenv("GITHUB_ACTOR"), 
						() -> System.getenv("GITHUB_TOKEN")
				)
		));
		
		resolver.addRepository(new MavenRepository(
				"GitHub Pkg 2", 
				"https://maven.pkg.github.com/m-marvin/app-javarun", 
				new Credentials(
						() -> System.getenv("GITHUB_ACTOR"), 
						() -> System.getenv("GITHUB_TOKEN")
				)
		));
		
		resolver.addRepository(new MavenRepository(
				"Central", 
				"https://repo.maven.apache.org/maven2", 
				null
		));
		
		System.out.println("GSON Test");
		
		resolver.resolveStr("com.google.code.gson:gson:2.9.1", "sources", "javadoc");

		System.out.println("JavaRun Test");
		
		resolver.resolveStr("de.m_marvin.javarun:javarun:1.2");

		System.out.println("GFrame Test");
		
		resolver.resolveStr("de.m_marvin.gframe:gframe:1.4.1");
		
//		String repository = "https://repo.maven.apache.org/maven2";
//		String dep = "com.google.code.gson:gson:2.9.1";
//		
//		String[] s = dep.split(":");
//		
//		String group = s[0];
//		String artifact = s[1];
//		String version = s[2];
//
//		System.out.println("repository: " + repository);
//		System.out.println("group: " + group);
//		System.out.println("artifact: " + artifact);
//		System.out.println("version: " + version);
//		
//		if (!URL.matcher(repository).find()) return;
//		if (!DEP.matcher(group).find()) return;
//		if (!DEP.matcher(artifact).find()) return;
//		if (!DEP.matcher(version).find()) return;
//		
//		String artifactURL = repository + "/" + group.replace('.', '/') + "/" + artifact;
//		String artifactMetaURL = artifactURL + "/" + ARTIFACT_META;
//		
//		System.out.println(artifactMetaURL);
//		
//		InputStream artifactMetaStream = new StringBufferInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
//				+ "<metadata modelVersion=\"1.1.0\">\r\n"
//				+ "  <groupId>com.google.code.gson</groupId>\r\n"
//				+ "  <artifactId>gson</artifactId>\r\n"
//				+ "  <versioning>\r\n"
//				+ "    <latest>2.11.0</latest>\r\n"
//				+ "    <release>2.11.0</release>\r\n"
//				+ "    <versions>\r\n"
//				+ "      <version>1.1</version>\r\n"
//				+ "      <version>1.4</version>\r\n"
//				+ "      <version>1.5</version>\r\n"
//				+ "      <version>1.6</version>\r\n"
//				+ "      <version>1.7</version>\r\n"
//				+ "      <version>1.7.1</version>\r\n"
//				+ "      <version>1.7.2</version>\r\n"
//				+ "      <version>2.0</version>\r\n"
//				+ "      <version>2.1</version>\r\n"
//				+ "      <version>2.2</version>\r\n"
//				+ "      <version>2.2.1</version>\r\n"
//				+ "      <version>2.2.2</version>\r\n"
//				+ "      <version>2.2.3</version>\r\n"
//				+ "      <version>2.2.4</version>\r\n"
//				+ "      <version>2.3</version>\r\n"
//				+ "      <version>2.3.1</version>\r\n"
//				+ "      <version>2.4</version>\r\n"
//				+ "      <version>2.5</version>\r\n"
//				+ "      <version>2.6</version>\r\n"
//				+ "      <version>2.6.1</version>\r\n"
//				+ "      <version>2.6.2</version>\r\n"
//				+ "      <version>2.7</version>\r\n"
//				+ "      <version>2.8.0</version>\r\n"
//				+ "      <version>2.8.1</version>\r\n"
//				+ "      <version>2.8.2</version>\r\n"
//				+ "      <version>2.8.3</version>\r\n"
//				+ "      <version>2.8.4</version>\r\n"
//				+ "      <version>2.8.5</version>\r\n"
//				+ "      <version>2.8.6</version>\r\n"
//				+ "      <version>2.8.7</version>\r\n"
//				+ "      <version>2.8.8</version>\r\n"
//				+ "      <version>2.8.9</version>\r\n"
//				+ "      <version>2.9.0</version>\r\n"
//				+ "      <version>2.9.1</version>\r\n"
//				+ "      <version>2.10</version>\r\n"
//				+ "      <version>2.10.1</version>\r\n"
//				+ "      <version>2.11.0</version>\r\n"
//				+ "    </versions>\r\n"
//				+ "    <lastUpdated>20240519190003</lastUpdated>\r\n"
//				+ "  </versioning>\r\n"
//				+ "</metadata>"); //queryHTTP(artifactMetaURL);
////		String artifactMetaStr = new String(artifactMetaStream.readAllBytes(), StandardCharsets.UTF_8);
////		System.out.println(artifactMetaStr);
//		DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
//		DocumentBuilder builder = factory.newDocumentBuilder();
//		Document artifactMetaDoc = builder.parse(artifactMetaStream);
//		artifactMetaStream.close();
//		
//		Node metadata = getNode(artifactMetaDoc, "metadata");
//		String groupNode = getNode(metadata, "groupId").getFirstChild().getNodeValue();
//		String artifactNode = getNode(metadata, "artifactId").getFirstChild().getNodeValue();
//		
//		if (!groupNode.equals(group) || !artifactNode.equals(artifact)) {
//			System.out.println("artifact/group missmatch!");
//			return;
//		}
//		
//		Node versioningNode = getNode(metadata, "versioning");
//		String latest = getNode(versioningNode, "latest").getFirstChild().getNodeValue();
//		String release = getNode(versioningNode, "release").getFirstChild().getNodeValue();
//		Node versionsNode = getNode(versioningNode, "versions");
//		List<String> versions = getStream(versionsNode).filter(n -> n.getNodeType() == 1).map(n -> n.getFirstChild().getNodeValue()).toList();
//		
//		System.out.println("latest: " + latest);
//		System.out.println("release: " + release);
//		for (String v : versions) System.out.println(" - " + v);
//		
//		if (versions.stream().filter(v -> v.equals(version)).count() == 0) {
//			System.out.println("invalid version");
//			return;
//		}
//		
//		String versionUrl = artifactURL + "/" + version;
//		String pomName = artifact + "-" + version + ".pom";
//		String pomUrl = versionUrl + "/" + pomName;
//		
//		System.out.println(pomUrl);
//		
//		InputStream pomStream = new StringBufferInputStream("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\r\n"
//				+ "  <modelVersion>4.0.0</modelVersion>\r\n"
//				+ "\r\n"
//				+ "  <parent>\r\n"
//				+ "    <groupId>com.google.code.gson</groupId>\r\n"
//				+ "    <artifactId>gson-parent</artifactId>\r\n"
//				+ "    <version>2.9.1</version>\r\n"
//				+ "  </parent>\r\n"
//				+ "\r\n"
//				+ "  <artifactId>gson</artifactId>\r\n"
//				+ "  <name>Gson</name>\r\n"
//				+ "\r\n"
//				+ "  <licenses>\r\n"
//				+ "    <license>\r\n"
//				+ "      <name>Apache-2.0</name>\r\n"
//				+ "      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>\r\n"
//				+ "    </license>\r\n"
//				+ "  </licenses>\r\n"
//				+ "\r\n"
//				+ "  <dependencies>\r\n"
//				+ "    <dependency>\r\n"
//				+ "      <groupId>junit</groupId>\r\n"
//				+ "      <artifactId>junit</artifactId>\r\n"
//				+ "      <scope>test</scope>\r\n"
//				+ "    </dependency>\r\n"
//				+ "  </dependencies>\r\n"
//				+ "\r\n"
//				+ "  <build>\r\n"
//				+ "    <plugins>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.apache.maven.plugins</groupId>\r\n"
//				+ "        <artifactId>maven-compiler-plugin</artifactId>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <id>default-compile</id>\r\n"
//				+ "            <configuration>\r\n"
//				+ "              <excludes>\r\n"
//				+ "                <!-- module-info.java is compiled using ModiTect -->\r\n"
//				+ "                <exclude>module-info.java</exclude>\r\n"
//				+ "              </excludes>\r\n"
//				+ "            </configuration>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <!-- Note: Javadoc plugin has to be run in combination with >= `package` \r\n"
//				+ "        phase, e.g. `mvn package javadoc:javadoc`, otherwise it fails with\r\n"
//				+ "        \"Aggregator report contains named and unnamed modules\" -->\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.apache.maven.plugins</groupId>\r\n"
//				+ "        <artifactId>maven-surefire-plugin</artifactId>\r\n"
//				+ "        <version>3.0.0-M7</version>\r\n"
//				+ "        <configuration>\r\n"
//				+ "          <!-- Deny illegal access, this is required for ReflectionAccessTest -->\r\n"
//				+ "          <!-- Requires Java >= 9; Important: In case future Java versions \r\n"
//				+ "            don't support this flag anymore, don't remove it unless CI also runs with \r\n"
//				+ "            that Java version. Ideally would use toolchain to specify that this should \r\n"
//				+ "            run with e.g. Java 11, but Maven toolchain requirements (unlike Gradle ones) \r\n"
//				+ "            don't seem to be portable (every developer would have to set up toolchain \r\n"
//				+ "            configuration locally). -->\r\n"
//				+ "          <argLine>--illegal-access=deny</argLine>\r\n"
//				+ "        </configuration>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.apache.maven.plugins</groupId>\r\n"
//				+ "        <artifactId>maven-javadoc-plugin</artifactId>\r\n"
//				+ "        <configuration>\r\n"
//				+ "          <excludePackageNames>com.google.gson.internal:com.google.gson.internal.bind</excludePackageNames>\r\n"
//				+ "        </configuration>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <!-- Add module-info to JAR, see https://github.com/moditect/moditect#adding-module-descriptors-to-existing-jar-files -->\r\n"
//				+ "      <!-- Uses ModiTect instead of separate maven-compiler-plugin executions \r\n"
//				+ "        for better Eclipse IDE support, see https://github.com/eclipse-m2e/m2e-core/issues/393 -->\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.moditect</groupId>\r\n"
//				+ "        <artifactId>moditect-maven-plugin</artifactId>\r\n"
//				+ "        <version>1.0.0.RC2</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <id>add-module-info</id>\r\n"
//				+ "            <phase>package</phase>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>add-module-info</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "            <configuration>\r\n"
//				+ "              <jvmVersion>9</jvmVersion>\r\n"
//				+ "              <module>\r\n"
//				+ "                <moduleInfoFile>${project.build.sourceDirectory}/module-info.java</moduleInfoFile>\r\n"
//				+ "              </module>\r\n"
//				+ "              <!-- Overwrite the previously generated JAR file, if any -->\r\n"
//				+ "              <overwriteExistingFiles>true</overwriteExistingFiles>\r\n"
//				+ "            </configuration>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>biz.aQute.bnd</groupId>\r\n"
//				+ "        <artifactId>bnd-maven-plugin</artifactId>\r\n"
//				+ "        <version>6.3.1</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>bnd-process</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.apache.maven.plugins</groupId>\r\n"
//				+ "        <artifactId>maven-jar-plugin</artifactId>\r\n"
//				+ "        <configuration>\r\n"
//				+ "          <archive>\r\n"
//				+ "            <!-- Use existing manifest generated by BND plugin -->\r\n"
//				+ "            <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>\r\n"
//				+ "          </archive>\r\n"
//				+ "        </configuration>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>org.codehaus.mojo</groupId>\r\n"
//				+ "        <artifactId>templating-maven-plugin</artifactId>\r\n"
//				+ "        <version>1.0.0</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <id>filtering-java-templates</id>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>filter-sources</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "            <configuration>\r\n"
//				+ "              <sourceDirectory>${basedir}/src/main/java-templates</sourceDirectory>\r\n"
//				+ "              <outputDirectory>${project.build.directory}/generated-sources/java-templates</outputDirectory>\r\n"
//				+ "            </configuration>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>com.coderplus.maven.plugins</groupId>\r\n"
//				+ "        <artifactId>copy-rename-maven-plugin</artifactId>\r\n"
//				+ "        <version>1.0.1</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <id>pre-obfuscate-class</id>\r\n"
//				+ "            <phase>process-test-classes</phase>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>rename</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "            <configuration>\r\n"
//				+ "              <fileSets>\r\n"
//				+ "                <fileSet>\r\n"
//				+ "                  <sourceFile>${project.build.directory}/test-classes/com/google/gson/functional/EnumWithObfuscatedTest.class</sourceFile>\r\n"
//				+ "                  <destinationFile>${project.build.directory}/test-classes-obfuscated-injar/com/google/gson/functional/EnumWithObfuscatedTest.class</destinationFile>\r\n"
//				+ "                </fileSet>\r\n"
//				+ "                <fileSet>\r\n"
//				+ "                  <sourceFile>${project.build.directory}/test-classes/com/google/gson/functional/EnumWithObfuscatedTest$Gender.class</sourceFile>\r\n"
//				+ "                  <destinationFile>${project.build.directory}/test-classes-obfuscated-injar/com/google/gson/functional/EnumWithObfuscatedTest$Gender.class</destinationFile>\r\n"
//				+ "                </fileSet>\r\n"
//				+ "              </fileSets>\r\n"
//				+ "            </configuration>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <groupId>com.github.wvengen</groupId>\r\n"
//				+ "        <artifactId>proguard-maven-plugin</artifactId>\r\n"
//				+ "        <version>2.6.0</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <phase>process-test-classes</phase>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>proguard</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "        <configuration>\r\n"
//				+ "          <obfuscate>true</obfuscate>\r\n"
//				+ "          <injar>test-classes-obfuscated-injar</injar>\r\n"
//				+ "          <outjar>test-classes-obfuscated-outjar</outjar>\r\n"
//				+ "          <inFilter>**/*.class</inFilter>\r\n"
//				+ "          <proguardInclude>${basedir}/src/test/resources/testcases-proguard.conf</proguardInclude>\r\n"
//				+ "          <libs>\r\n"
//				+ "            <lib>${project.build.directory}/classes</lib>\r\n"
//				+ "            <lib>${java.home}/jmods/java.base.jmod</lib>\r\n"
//				+ "          </libs>\r\n"
//				+ "        </configuration>\r\n"
//				+ "      </plugin>\r\n"
//				+ "      <plugin>\r\n"
//				+ "        <artifactId>maven-resources-plugin</artifactId>\r\n"
//				+ "        <version>3.3.0</version>\r\n"
//				+ "        <executions>\r\n"
//				+ "          <execution>\r\n"
//				+ "            <id>post-obfuscate-class</id>\r\n"
//				+ "            <phase>process-test-classes</phase>\r\n"
//				+ "            <goals>\r\n"
//				+ "              <goal>copy-resources</goal>\r\n"
//				+ "            </goals>\r\n"
//				+ "            <configuration>\r\n"
//				+ "              <outputDirectory>${project.build.directory}/test-classes/com/google/gson/functional</outputDirectory>\r\n"
//				+ "              <resources>\r\n"
//				+ "                <resource>\r\n"
//				+ "                  <directory>${project.build.directory}/test-classes-obfuscated-outjar/com/google/gson/functional</directory>\r\n"
//				+ "                  <includes>\r\n"
//				+ "                    <include>EnumWithObfuscatedTest.class</include>\r\n"
//				+ "                    <include>EnumWithObfuscatedTest$Gender.class</include>\r\n"
//				+ "                  </includes>\r\n"
//				+ "                </resource>\r\n"
//				+ "              </resources>\r\n"
//				+ "            </configuration>\r\n"
//				+ "          </execution>\r\n"
//				+ "        </executions>\r\n"
//				+ "      </plugin>\r\n"
//				+ "    </plugins>\r\n"
//				+ "  </build>\r\n"
//				+ "</project>\r\n"
//				+ "\r\n"
//				+ "");//queryHTTP(pomUrl);
////		String pomStreamStr = new String(pomStream.readAllBytes(), StandardCharsets.UTF_8);
////		System.out.println(pomStreamStr);
//		Document pomDoc = builder.parse(pomStream);
//		pomStream.close();
//		
//		System.out.println(pomDoc);
//		
	}
//	
//	public static Stream<Node> getStream(Node parent) {
//		return IntStream.range(0, parent.getChildNodes().getLength()).mapToObj(i -> parent.getChildNodes().item(i));
//	}
//	
//	public static Node getNode(Node parent, String name) {
//		NodeList l = parent.getChildNodes();
//		for (int i = 0; i < l.getLength(); i++) {
//			if (l.item(i).getNodeName().equals(name)) return l.item(i);
//		}
//		return null;
//	}
//	
//	public static InputStream queryHTTP(String urlstr) throws IOException {
//		URL url = new URL(urlstr);
//		HttpURLConnection http = (HttpURLConnection) url.openConnection();
//		http.setRequestMethod("GET");
//		http.connect();
//		
//		int rcode = http.getResponseCode();
//		if (rcode != 200) throw new IOException("error response code: " + rcode);
//		return http.getInputStream();
//	}
	
}
