package de.m_marvin.metabuild.api.core.devenv;

import java.io.File;
import java.util.List;

public interface IJavaSourceIncludes extends ISourceIncludes {

	public static final String JAVA_LANGUAGE_ID = "java";

	@Override
	default String languageId() {
		return JAVA_LANGUAGE_ID;
	}
	
	public List<File> getSourceJars();
	
}
