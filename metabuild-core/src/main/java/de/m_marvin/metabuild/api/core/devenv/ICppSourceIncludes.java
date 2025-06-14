package de.m_marvin.metabuild.api.core.devenv;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface ICppSourceIncludes extends ISourceIncludes {

	public static final String CPP_LANGUAGE_ID = "cpp";

	@Override
	default String languageId() {
		return CPP_LANGUAGE_ID;
	}
	
	public List<File> getIncludeDirectories();
	public Map<String, String> getSymbols();
	
}
