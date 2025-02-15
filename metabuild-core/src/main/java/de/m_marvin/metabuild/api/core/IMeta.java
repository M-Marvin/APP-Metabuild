package de.m_marvin.metabuild.api.core;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.api.core.devenv.ISourceIncludes;
import de.m_marvin.metabuild.api.core.tasks.MetaGroup;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

/**
 * Base API of meta build system.
 */
public interface IMeta {

	public interface IStatusCallback {
		
		public void taskCount(int taskCount);
		public void taskStarted(String task);
		public void taskStatus(String task, String status);
		public void taskCompleted(String task);
		public void buildCompleted(boolean success);
		
	}
	
	public static final String METABUILD_MAIN_CLASS = "de.m_marvin.metabuild.core.Metabuild";
	
	public static final String META_VERSION_PROPERTY = "meta.version";
	public static final String META_TITLE_PROPERTY = "meta.title";
	public static final String META_HOME_PROPERTY = "meta.home";
	
	public static final File DEFAULT_BUILD_FILE_NAME = new File("build.meta");
	public static final File DEFAULT_BUILD_LOG_NAME = new File("build.log");
	public static final File DEFAULT_CACHE_DIRECTORY = new File(System.getProperty("user.home") + "/.meta");
	public static final int DEFAULT_TASK_THREADS = 8;
	
	public static final String BUILD_SCRIPT_CLASS_NAME = "Buildfile";
	public static final Pattern TASK_NAME_FILTER = Pattern.compile("[\\d\\w]+");

	public static final String DEPENDENCY_TASK_GROUP = "depend";
	public static final String META_PLUGIN_LOCATION = "meta/plugins";
	
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
	 * If set to true, re-query all dependencies from online.<br>
	 * This will only be active for the next build cycle, and be reset to false after that.
	 * @param refreshDependencies true to re-query all dependencies
	 */
	public void setRefreshDependencies(boolean refreshDependencies);
	
	/**
	 * If set to true, all tasks and their dependencies are run even if they are up to date.<br>
	 * This will only be active for the next build cycle, and be reset to false after that.
	 * @param forceRunTasks true to force all tasks to run
	 */
	public void setForceRunTasks(boolean forceRunTasks);
	
	/**
	 * @param taskThreads The max. number of threads to use in parallel to run the build tasks
	 */
	public void setTaskThreads(int taskThreads);
	
	/**
	 * @param statusCallback A callback to receive status updates about the running tasks
	 */
	public void addStatusCallback(IStatusCallback statusCallback);
	
	/**
	 * Configures an output for raw terminal ANSI formatted output.<br>
	 * Usually directed to the terminal to print the graphical interface.
	 * @param print The print stream to write the terminal output to
	 * @param printUI If an graphical interface should be printed, or just raw log output
	 */
	public void setTerminalOutput(PrintStream print, boolean printUI);
	
	/**
	 * Sets the source of the console input.<br>
	 * Usually only used by sub-processes started by meta, such as test runs of user code.
	 */
	public void setConsoleStreamInput(InputStream input);
	
	/**
	 * Sets the destination of the log output stream.<br>
	 * Can either be an OutputStream or an Logger instance
	 */
	public void setLogStreamOutput(Object output);
	
	/**
	 * @return The working directory of the current metabuild instance
	 */
	public File workingDir();
	
	/**
	 * @return The metabuild installation directory
	 */
	public File metaHome();
	
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
		return initBuild(DEFAULT_BUILD_FILE_NAME);
	}

	/**
	 * Returns a list of all tasks and groups registered by the current build file
	 */
	public <T> void getTasks(T ref, Collection<MetaGroup<T>> groups, Collection<MetaTask<T>> tasks);
	
	/**
	 * Returns a list of source includes required by the lastly run tasks.<br>
	 * These are dependencies that are required to be present in the development environment (IDE).
	 * @param includes A list to fill with all source includes required
	 */
	public void getSourceIncludes(Collection<ISourceIncludes> includes);
	
	/**
	 * Returns a list of source includes required by the lastly run tasks.<br>
	 * These are dependencies that are required to be present in the development environment (IDE).
	 * @param language The language for which to request the source includes
	 * @param includes A list to fill with all source includes required for the spcified language
	 */
	public <T extends ISourceIncludes> void getSourceIncludes(Collection<T> includes, String language);
	
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
