package de.m_marvin.metabuild.cpp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import de.m_marvin.metabuild.api.core.devenv.ICppSourceIncludes;
import de.m_marvin.metabuild.core.Metabuild;

public class CppSourceIncludes implements ICppSourceIncludes {
	
	public final List<File> includeDirectories = new ArrayList<File>();
	
	public CppSourceIncludes(Collection<File> directories) {
		this.includeDirectories.addAll(directories);
	}
	
	@Override
	public List<File> getIncludeDirectories() {
		return includeDirectories;
	}
	
	/**
	 * Used to pass include directories of dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 */
	public static void include(Collection<File> includes) {
		Metabuild.get().addSourceInclude(new CppSourceIncludes(includes));
	}

	/**
	 * Used to pass include directories of dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 */
	public static void include(File... includes) {
		include(Arrays.asList(includes));
	}
	
}
