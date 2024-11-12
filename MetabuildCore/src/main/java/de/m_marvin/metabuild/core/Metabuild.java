package de.m_marvin.metabuild.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaInitError;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.script.BuildTask;
import de.m_marvin.metabuild.core.script.compile.ScriptCompiler;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.simplelogging.Log;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.MultiLogger;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.StreamLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;

public final class Metabuild {

	public static final String LOG_TAG = "Metabuild";
	
	public static final String DEFAULT_BUILD_FILE_NAME = "build.meta";
	public static final String DEFAULT_BUILD_LOG_NAME = "build/build.log";
	public static final String DEFAULT_BUILD_DIRECTORY = "build";
	public static final String DEFAULT_CACHE_DIRECTORY = System.getProperty("user.home") + "/.meta";
	
	public static final String BUILD_SCRIPT_CLASS_NAME = "Buildfile";
	public static final Pattern TASK_NAME_FILTER = Pattern.compile("[\\d\\w]+");
	
	private static Metabuild instance;
	
	private final File workingDirectory;
	private File buildDirectory;
	private File cacheDirectory;
	private File logFile;
	private Logger logger;
	private OutputStream logStream;
	private IStatusCallback statusCallback;
	private BuildScript buildscript;
	
	private final ScriptCompiler buildCompiler;
	private final Map<String, BuildTask> registeredTasks = new HashMap<>();
	
	public Metabuild(File workingDirectory) {
		if (instance != null) throw MetaInitError.msg("can't instantiate multiple metabuild instances in same VM!");
		instance = this;
		
		this.workingDirectory = workingDirectory;
		setBuildDirectory(new File(DEFAULT_BUILD_DIRECTORY));
		setCacheDirectory(new File(DEFAULT_CACHE_DIRECTORY));
		
		this.buildCompiler = new ScriptCompiler(this);
	}
	
	public void close() {
		if (this.logStream != null) {
			try {
				this.logStream.close();
			} catch (IOException e) {}
			this.logFile = null;
		}
	}
	
	public static void terminate() {
		if (instance != null)
			instance.close();
		instance = null;
	}

	public void setBuildDirectory(File buildDirectory) {
		this.buildDirectory = FileUtility.absolute(buildDirectory);
	}

	public void setCacheDirectory(File cacheDirectory) {
		this.cacheDirectory = FileUtility.absolute(cacheDirectory);
	}

	public void setLogFile(File logFile) {
		this.logFile = FileUtility.absolute(logFile);
	}
	
	public void setStatusCallback(IStatusCallback statusCallback) {
		this.statusCallback = statusCallback;
	}
	
	public static Metabuild get() {
		if (instance == null) throw MetaInitError.msg("metabuild instance not yet created in this VM!");
		return instance;
	}
	
	public Logger logger() {
		return this.logger != null ? this.logger : Log.defaultLogger();
	}
	
	public boolean registerTask(String name, BuildTask task) {
		if (!TASK_NAME_FILTER.matcher(name).matches()) {
			logger().warnt(LOG_TAG, "Task name '%s' is invalid, needs to match [\\d\\w]+!", name);
			return false;
		}
		if (this.registeredTasks.containsKey(name)) {
			logger().warnt(LOG_TAG, "Task '%s' already registered!", name);
			return false;
		}
		this.registeredTasks.put(name, task);
		return true;
	}
	
	public BuildTask taskNamed(String name) {
		if (!this.registeredTasks.containsKey(name)) {
			logger.warnt(LOG_TAG, "No task named '%s' is registered!");
			return null;
		}
		return this.registeredTasks.get(name);
	}
	
	public boolean initDirectories() {
		if (!this.cacheDirectory.isDirectory() && !this.cacheDirectory.mkdir()) {
			logger().errort(LOG_TAG, "could not create cache directory: %s", this.cacheDirectory.getPath());
			return false;
		}
		if (!this.buildDirectory.isDirectory() && !this.buildDirectory.mkdir()) {
			logger().errort(LOG_TAG, "could not create build directory: %s", this.buildDirectory.getPath());
			return false;
		}
		if (this.logger == null) {
			try {
				this.logStream = new FileOutputStream(this.logFile);
				
				this.logger = new StacktraceLogger(new MultiLogger(new StreamLogger(logStream), new SystemLogger()));
			} catch (FileNotFoundException e) {
				logger().warnt(LOG_TAG, "failed to create log file: " + e.getMessage());
				return false;
			}
			
			// Print version info to log
			String titleInfo = Metabuild.class.getPackage().getImplementationTitle();
			String versionInfo = Metabuild.class.getPackage().getImplementationVersion();
			logger().infot(LOG_TAG, "#########################################");
			logger().infot(LOG_TAG, "      %s ver%s", titleInfo, versionInfo);
			logger().infot(LOG_TAG, "#########################################");
			
		}
		return true;
	}
	
	public File buildDir() {
		return this.buildDirectory;
	}
	
	public File workingDir() {
		return this.workingDirectory;
	}
	
	public File cacheDir() {
		return this.cacheDirectory;
	}
	
	public ScriptCompiler getBuildCompiler() {
		return buildCompiler;
	}
	
	public boolean initBuild() {
		return initBuild(new File(DEFAULT_BUILD_FILE_NAME));
	}
	
	public boolean initBuild(File buildFile) {
		if (!initDirectories()) return false;
		this.registeredTasks.clear();

		this.buildscript = this.buildCompiler.loadBuildFile(buildFile);
		if (this.buildscript == null) {
			logger().errort(LOG_TAG, "failed to load buildfile, build aborted!");
			return false;
		}
		
		logger().infot(LOG_TAG, "buildfile: %s", buildFile.getName());
		
		try {
			
			this.buildscript.init();
			
		} catch (BuildScriptException e) {
			logger().errort(LOG_TAG, "buildfile init phase failed!");
			e.printStack(logger().errorPrinter(LOG_TAG));
		} catch (Throwable e) {
			logger().errort(LOG_TAG, "buildfile threw uncatched exception:", e);
			return false;
		}
		
		return true;
	}
	
	public boolean runTask(String taskName) {
		// TODO run tasks
		return true;
	}
	
}
