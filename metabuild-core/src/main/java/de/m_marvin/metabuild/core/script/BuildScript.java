package de.m_marvin.metabuild.core.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

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
		return field(this, fieldName);
	}
	
	protected Object field(BuildScript buildscript, String fieldName) {
		try {
			Field f = buildscript.getClass().getDeclaredField(fieldName);
			f.setAccessible(true);
			return f.get(buildscript);
		} catch (Exception e) {
			throw BuildScriptException.msg("field '%s' is not availble in build '%s'", fieldName, this.buildName);
		}
	}

	/**
	 * Hold property mapping defined by this build script or imported from an properties file.
	 */
	public Map<String, String> properties = new HashMap<>();
	
	protected void importProperties(File location) {
		try {
			BufferedReader pin = new BufferedReader(new InputStreamReader(new FileInputStream(FileUtility.absolute(location))));
			String line;
			while ((line = pin.readLine()) != null) {
				int i = line.indexOf('=');
				if (i == -1) continue;
				String key = line.substring(0, i);
				String value = line.substring(i + 1);
				this.properties.put(key.strip(), value.strip());
			}
			pin.close();
		} catch (IOException e) {
			throw BuildScriptException.msg("could not import properties file '%s' in build '%s'", location, this.buildName);
		}
	}
	
	private static final Pattern PROP_KEY = Pattern.compile("\\$\\{([^\\s${}]+)}");
	
	protected String fillProperties(String str) {
		return PROP_KEY.matcher(str).replaceAll(m -> this.properties.getOrDefault(m.group(1), m.group(1)));
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
