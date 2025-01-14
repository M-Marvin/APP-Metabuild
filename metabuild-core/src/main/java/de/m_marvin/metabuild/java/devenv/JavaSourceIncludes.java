package de.m_marvin.metabuild.java.devenv;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.m_marvin.metabuild.api.core.devenv.IJavaSourceIncludes;
import de.m_marvin.metabuild.core.Metabuild;

public class JavaSourceIncludes implements IJavaSourceIncludes {

	public final List<File> sourceJars = new ArrayList<File>();
	
	public JavaSourceIncludes() {
		// TODO store in buildskript and upload when finished
	}
	
	@Override
	public List<File> getSourceJars() {
		return this.sourceJars;
	}
	
}
