package de.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import de.m_marvin.cmode.Console;
import de.m_marvin.cmode.Console.Mode;
import de.m_marvin.cmode.NonBlockingInputStream;
import de.m_marvin.metabuild.maven.DependencyResolver;
import de.m_marvin.metabuild.maven.MavenResolver.MavenRepository;
import de.m_marvin.metabuild.maven.MavenResolver.MavenRepository.Credentials;
import de.m_marvin.metabuild.maven.MavenResolver.POM.Scope;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;

public class Test {
	
	public static final Pattern URL = Pattern.compile("[0-9A-Za-z$\\-_\\.+!*#'\\(\\)]+");
	public static final Pattern DEP = Pattern.compile("[0-9a-z\\-\\.]+");
	public static final String ARTIFACT_META = "maven-metadata.xml";
	
	public static DependencyResolver resolver = null;
	
	public static void main(String[] args) throws Exception {
		
		if (!Console.ansiMode()) System.exit(-1);
		NonBlockingInputStream in = new NonBlockingInputStream(System.in);
		System.out.write(new String("test \033[6n").getBytes(StandardCharsets.UTF_8));
		String s = new String(in.readAllBytes());
		Console.defaultMode();
		
		System.out.println("response: " + s);
		System.out.println("response: " + s.length());
		
		
//		resolver = new DependencyResolver(new File("run/test"), new StacktraceLogger(new SystemLogger())); //new MavenResolver(new File("run/test"), new StacktraceLogger(new SystemLogger()));
//		
//		resolver.addRepository(new MavenRepository(
//				"GitHub Pkg 1", 
//				"https://maven.pkg.github.com/m-marvin/library-graphicsframework", 
//				new Credentials(
//						() -> System.getenv("GITHUB_ACTOR"), 
//						() -> System.getenv("GITHUB_TOKEN")
//				)
//		));
//
//		resolver.addRepository(new MavenRepository(
//				"GitHub Pkg 2", 
//				"https://maven.pkg.github.com/m-marvin/library-unifiedvectors", 
//				new Credentials(
//						() -> System.getenv("GITHUB_ACTOR"), 
//						() -> System.getenv("GITHUB_TOKEN")
//				)
//		));
//		
//		resolver.addRepository(new MavenRepository(
//				"GitHub Pkg 3", 
//				"https://maven.pkg.github.com/m-marvin/app-javarun", 
//				new Credentials(
//						() -> System.getenv("GITHUB_ACTOR"), 
//						() -> System.getenv("GITHUB_TOKEN")
//				)
//		));
//		
//		resolver.addRepository(new MavenRepository(
//				"Central", 
//				"https://repo.maven.apache.org/maven2", 
//				null
//		));
//
//		resolver.addRepository(new MavenRepository(
//				"MVN", 
//				"https://mvnrepository.com/artifact", 
//				null
//		));
//		
//		System.out.println("GSON Test");
//		
//		resolver.addDependency("com.google.code.gson:gson:2.9.1");
//
//		System.out.println("JavaRun Test");
//		
//		resolver.addDependency("de.m_marvin.javarun:javarun:1.2");
//
//		System.out.println("GFrame Test");
//		
//		resolver.addDependency("de.m_marvin.gframe:gframe:1.4.2");
//		
////		resolver.resolveDependencies(s -> s == Scope.COMPILE);
//		
//		for (String dep : resolver.getDependencyJarPaths().keySet()) {
//			System.out.println(dep);
//			System.out.println(resolver.getDependencyJarPaths().get(dep));
//		}
		
	}
	
}
