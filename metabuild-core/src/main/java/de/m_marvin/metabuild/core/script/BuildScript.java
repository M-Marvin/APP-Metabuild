package de.m_marvin.metabuild.core.script;

import java.io.File;
import java.lang.reflect.Field;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.InitTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.simplelogging.api.Logger;

/**
 * The base class every build script has to extend from.<br>
 * It defines the main methods called from the build system.
 */
public class BuildScript {

	/* Contains multi-project management information about this build script
	 * Filled out by the metabuild main class when importing the build file */
	public String buildName = "";
	public File buildfileLocation = new File("");

	protected void importBuild(File locationOrBuildFile) {
		importBuild(null, locationOrBuildFile);
	}
	
	protected void importBuild(String importName, File locationOrBuildFile) {
		locationOrBuildFile = FileUtility.absolute(locationOrBuildFile);
		if (locationOrBuildFile.isDirectory()) {
			Metabuild.get().importBuild(importName, locationOrBuildFile, null);
		} else {
			Metabuild.get().importBuild(importName, null, locationOrBuildFile);
		}
	}
	
	protected void importBuild(String importName, File location, File buildfile) {
		Metabuild.get().importBuild(importName, location, buildfile);
	}

	protected BuildScript buildNamed(String importName) {
		return Metabuild.get().buildNamed(importName);
	}
	
	protected Object field(String fieldName) {
		try {
			Field f = this.getClass().getDeclaredField(fieldName);
			f.setAccessible(true);
			return f.get(this);
		} catch (Exception e) {
			throw BuildScriptException.msg("field '%s' is not availble in build '%s'", fieldName, this.buildName);
		}
	}
	
	/**
	 * Called once after the build file was successfully compiled and laoded.<br>
	 * Creates and registers all tasks.
	 */
	public void init() {
		
		InitTask task = new InitTask("init");
		task.group = "setup";
		
	}
	
	/**
	 * Called after all tasks that where started finished successfully.
	 */
	public void finish() {}
	
	/**
	 * Helper method to get an logger instance for printing messages.
	 */
	protected Logger logger() {
		return Metabuild.get().logger();
	}

	/**
	 * Helper method to get an task by its name.
	 */
	protected BuildTask taskNamed(String name) {
		return Metabuild.get().taskNamed(name);
	}
	
	@Override
	public String toString() {
		return "BuildScript[" + this.buildName + "]";
	}
	
}
