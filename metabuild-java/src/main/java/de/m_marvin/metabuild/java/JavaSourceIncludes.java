package de.m_marvin.metabuild.java;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import de.m_marvin.metabuild.api.core.devenv.IJavaSourceIncludes;
import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.util.FileUtility;

public class JavaSourceIncludes implements IJavaSourceIncludes {
	
	public final Map<File, File> sourceAttachments = new HashMap<>();
	public final Map<File, File> javadocAttachments = new HashMap<>();
	public final List<File> sourceJars = new ArrayList<File>();
	
	public JavaSourceIncludes(Collection<File> files, Map<File, File> sourceAttachments, Map<File, File> javadocAttachments) {
		this.sourceJars.addAll(files);
		this.sourceAttachments.putAll(sourceAttachments);
		this.javadocAttachments.putAll(javadocAttachments);
	}
	
	@Override
	public List<File> getSourceJars() {
		return this.sourceJars;
	}
	
	@Override
	public Map<File, File> getSourceAttachments() {
		return this.sourceAttachments;
	}

	@Override
	public Map<File, File> getJavadocAttachments() {
		return this.javadocAttachments;
	}
	
	/**
	 * Used to pass dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 * Files part of the same dependency should be grouped to arrays, and are sorted for binaries, sources and javadoc by this method by the configuration extension names.
	 */
	public static void include(Collection<File[]> includes) {
		
		List<File> binaries = new ArrayList<File>();
		Map<File, File> sources = new HashMap<File, File>();
		Map<File, File> javadocs = new HashMap<File, File>();
		
		for (File[] depFiles : includes) {
			
			Optional<File> sourcesFile = Stream.of(depFiles).filter(f -> FileUtility.getNameNoExtension(f).endsWith("-sources")).findFirst();
			Optional<File> javadocFile = Stream.of(depFiles).filter(f -> FileUtility.getNameNoExtension(f).endsWith("-javadoc")).findFirst();
			List<File> binFiles = Stream.of(depFiles).filter(f -> {
				if (sourcesFile.isPresent() && sourcesFile.get().equals(f)) return false;
				if (javadocFile.isPresent() && javadocFile.get().equals(f)) return false;
				if (!FileUtility.getExtension(f).equals("jar")) return false;
				return true;
			}).toList();
			
			for (File binaryFile : binFiles) {
				binaries.add(binaryFile);
				if (sourcesFile.isPresent())
					sources.put(binaryFile, sourcesFile.get());
				if (javadocFile.isPresent())
					javadocs.put(binaryFile, javadocFile.get());
			}
			
		}
		
		Metabuild.get().addSourceInclude(new JavaSourceIncludes(binaries, sources, javadocs));
	}

	/**
	 * Used to pass dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 * Files part of the same dependency are grouped to arrays, and sorted for binaries, sources and javadoc by this method by the configuration extension names.
	 */
	public static void include(File[]... jarFiles) {
		include(Arrays.asList(jarFiles));
	}
	
}
