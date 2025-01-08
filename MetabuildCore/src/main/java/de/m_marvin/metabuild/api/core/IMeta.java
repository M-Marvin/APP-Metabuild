package de.m_marvin.metabuild.api.core;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.regex.Pattern;

import de.m_marvin.simplelogging.api.Logger;

/**
 * Base API of meta build system.
 */
public interface IMeta {

	public interface IStatusCallback {
		
		public void taskCount(int taskCount);
		public void taskStarted(String task);
		public void taskStatus(String task, String status);
		public void taskCompleted(String task);
		
	}
	
	public static final String METABUILD_MAIN_CLASS = "de.m_marvin.metabuild.core.Metabuild";
	
	public static final String META_VERSION_PROPERTY = "meta.version";
	public static final String META_TITLE_PROPERTY = "meta.title";
	public static final String META_HOME_PROPERTY = "meta.home";
	
	public static final String DEFAULT_BUILD_FILE_NAME = "build.meta";
	public static final String DEFAULT_BUILD_LOG_NAME = "build.log";
	public static final String DEFAULT_CACHE_DIRECTORY = System.getProperty("user.home") + "/.meta";
	public static final int DEFAULT_TASK_THREADS = 8;
	
	public static final String BUILD_SCRIPT_CLASS_NAME = "Buildfile";
	public static final Pattern TASK_NAME_FILTER = Pattern.compile("[\\d\\w]+");

	/**
	 * Get a new instance of the metabuild main class.<br>
	 * Only one instance can exist within a JVM at a time.
	 * @return
	 */
	public static IMeta instantiateMeta(ClassLoader classLoader) throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			Class<? extends IMeta> metaclass = (Class<? extends IMeta>) classLoader.loadClass(METABUILD_MAIN_CLASS);
			return metaclass.getConstructor().newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new LayerInstantiationException("could not create dynamic metabuild instance!", e);
		}
	}

	/**
	 * Closes and releases the metabuild instance, after that, a new instance can be created.
	 */
	public void terminate();
	
	/**
	 * Closes the metabuild instance, but does not release the static instance variable (still no new instance can be created after closing)
	 */
	public void close();
	
	/**
	 * @param cacheDirectory The directory to store cache files, such as downloaded dependencies
	 */
	public void setCacheDirectory(File cacheDirectory);

	/**
	 * @param logFile The log file to write to
	 */
	public void setLogFile(File logFile);
	
	/**
	 * If set to true, re-query all dependencies from online<br>
	 * This will only be active for the next build cycle, and be reset to false after that.
	 * @param refreshDependencies true to re-query all dependencies
	 */
	public void setRefreshDependencies(boolean refreshDependencies);
	
	/**
	 * @param taskThreads The max. number of threads to use in parallel to run the build tasks
	 */
	public void setTaskThreads(int taskThreads);
	
	public void setStatusCallback(IStatusCallback statusCallback);
	
	public void setTerminalOutput(boolean output);
	
	public Logger logger();
	
	/**
	 * @return The working directory of the current metabuild instance
	 */
	public File workingDir();
	
	/**
	 * @return The cache directory of the current metabuild instance
	 */
	public File cacheDir();

	/**
	 * Sets the working directory, should be set to the project root before loading the build file.
	 * @param workingDirectory
	 */
	public void setWorkingDirectory(File workingDirectory);
	
	/**
	 * Attempts to initialize using the build file at the default location.
	 * @return true if and only if the init phase did complete successfully.
	 */
	public default boolean initBuild() {
		return initBuild(new File(DEFAULT_BUILD_FILE_NAME));
	}

	/**
	 * Attempts to initialize using the build file at specified location.
	 * @return true if and only if the init phase did complete successfully.
	 */
	public boolean initBuild(File buildFile);
	
	/**
	 * Prepares all requested tasks and their dependencies for execution and executes them.
	 * @param tasks The tasks to run
	 * @return true if and only if all tasks where prepared and executed successfully
	 */
	public boolean runTasks(String... tasks);
	
	/**
	 * Prepares all requested tasks and their dependencies for execution and executes them.
	 * @param tasks The tasks to run
	 * @return true if and only if all tasks where prepared and executed successfully
	 */
	public boolean runTasks(List<String> tasks);
	
}
