package de.m_marvin.metabuild.api.core.devenv;

import java.io.File;
import java.util.List;

public interface ICppSourceIncludes extends ISourceIncludes {

	public static final String CPP_LANGUAGE_ID = "cpp";

	@Override
	default String languageId() {
		return CPP_LANGUAGE_ID;
	}
	
	public List<File> getIncludeDirectories();
	
}
