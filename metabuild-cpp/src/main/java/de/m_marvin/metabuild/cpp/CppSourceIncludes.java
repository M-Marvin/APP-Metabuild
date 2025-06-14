package de.m_marvin.metabuild.cpp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.m_marvin.metabuild.api.core.devenv.ICppSourceIncludes;
import de.m_marvin.metabuild.core.Metabuild;

public class CppSourceIncludes implements ICppSourceIncludes {
	
	public final List<File> includeDirectories = new ArrayList<File>();
	public final Map<String, String> symbolDefinitions = new HashMap<String, String>();
	
	public CppSourceIncludes(Collection<File> directories, Map<String, String> symbols) {
		this.includeDirectories.addAll(directories);
		this.symbolDefinitions.putAll(symbols);
	}
	
	@Override
	public List<File> getIncludeDirectories() {
		return includeDirectories;
	}
	
	@Override
	public Map<String, String> getSymbols() {
		return symbolDefinitions;
	}
	
	/**
	 * Used to pass include directories of dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 */
	public static void include(Map<String, String> symbols, Collection<File> includes) {
		Metabuild.get().addSourceInclude(new CppSourceIncludes(includes, symbols));
	}

	/**
	 * Used to pass include directories of dependencies required by this project to external software running the metabuild system, usually an IDE.<br>
	 */
	public static void include(Map<String, String> symbols, File... includes) {
		include(symbols, Arrays.asList(includes));
	}
	
}
