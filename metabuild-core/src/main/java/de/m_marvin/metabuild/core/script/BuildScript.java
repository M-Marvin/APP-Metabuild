package de.m_marvin.metabuild.core.script;

/**
 * The base class every build script has to extend from.<br>
 * It defines the main methods called from the build system.
 */
public class BuildScript {
	
	/**
	 * Called once after the build file was successfully compiled and laoded.<br>
	 * Creates and registers all tasks.
	 */
	public void init() {}
	
	/**
	 * Called after all tasks that where started, finished successfully.
	 */
	public void finish() {}
	
}
