package de.m_marvin.metabuild.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import de.m_marvin.basicxml.internal.StackList;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.devenv.ISourceIncludes;
import de.m_marvin.metabuild.api.core.tasks.MetaGroup;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;
import de.m_marvin.metabuild.core.cli.OutputHandler;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaInitError;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.script.compile.ScriptCompiler;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.tasks.BuildTask.TaskState;
import de.m_marvin.metabuild.core.tasks.RootTask;
import de.m_marvin.metabuild.core.util.DynamicFileListClassLoader;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.simplelogging.Log;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.MultiLogger;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.StreamLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;

/**
 * Main class of the metabuild system
 */
public final class Metabuild implements IMeta {
	
	public static final String LOG_TAG = "Metabuild";

	private static Metabuild instance;
	
	/* Working directory of metabuild, normally the project root */
	private File workingDirectory;
	/* Meta home directory */
	private File metaHome;
	/* Cache directory for metadata and downloaded files, normall user directory */
	private File cacheDirectory;
	/* Log file to store logging output */
	private File logFile;
	/* OutputStream to the log file */
	private OutputStream logStream;
	/* Handling of console input */
	private PrintWriter consoleStreamTarget = null;
	private InputStream consoleStream = System.in;
	private boolean consolePipeClosed = true;
	/* Root logger, all loggers end up here */
	private Logger logger;
	/* If the logger output is printed to the terminal */
	private Logger terminalLogger = new SystemLogger();
	/* Number of allowed tasks to spawn for processing tasks in parallel */
	private int taskThreads;
	/* Set to true if the next build process should re-download all external dependencies */
	private boolean refreshDependencies = false;
	/* Set to true if the next build process should skip the actual execution of tasks, and only run the prepare phase */
	private boolean skipTaskRun = false;
	/* If all tasks should be run even if they are up to date */
	private boolean forceRunTasks = false;
	/* Current state of this metabuild instance */
	private MetaState phase = MetaState.PREINIT;
	/* Currently active build script instance */
	private StackList<BuildScript> buildstack = new StackList<>();
	/* Imported build script instances */
	private Map<String, BuildScript> imports = new HashMap<String, BuildScript>();
	/* Import aliases */
	private Map<String, String> importAlias = new HashMap<String, String>();
	/* Task to TaskNode map, nodes combine one BuildTask and its dependent tasks */
	private Map<BuildTask, TaskNode> task2node = new HashMap<>();
	/* Root TaskNode for the current build task tree */
	private TaskNode taskTree;
	/* Queue of build tasks currently executed by the task threads */
	private BlockingQueue<Runnable> taskQueue;
	/* Executor for build tasks */
	private ThreadPoolExecutor taskExecutor;
	/* Set if an external abort request was issued and reset if abort succedded */
	private boolean doAbort = false;
	/* Compiler for loading and instantiating build script */
	private final ScriptCompiler buildCompiler;
	/* Map of registered tasks of the current build script */
	private final Map<String, BuildTask> registeredTasks = new HashMap<>();
	/* Map of registered task dependencies of current build script */
	private final Map<String, Set<String>> taskDependencies = new HashMap<>();
	/* Status callback to report back build progress */
	private Set<IStatusCallback> statusCallback = new HashSet<>();
	/* List of includes for the developement environment */
	private List<ISourceIncludes> sourceIncludes = new ArrayList<>();
	/* An handler for printing the command line inteface, can be null if not configured */
	@SuppressWarnings("unused")
	private OutputHandler outputHandler = null;
	/* ClassLoader able to load all plugins found in any project which has been loaded in this session */
	private DynamicFileListClassLoader pluginLoader = new DynamicFileListClassLoader(Thread.currentThread().getContextClassLoader());
	
	private String metabuildTitle;
	private String metabuildVersion;
	
	/**
	 * Instantiates a new metabuild instance.<br>
	 * Only one instance can be created in the runtime environment at a time
	 * @param workingDirectory The working directory of the instance, usual the directory containing the projects build file
	 */
	public Metabuild() {
		if (instance != null) throw MetaInitError.msg("can't instantiate multiple metabuild instances in same VM!");
		instance = this;
		
		setLogFile(DEFAULT_BUILD_LOG_NAME);
		setTaskThreads(DEFAULT_TASK_THREADS);
		
		this.buildCompiler = new ScriptCompiler(this);
		
		// Set meta properties
		this.metabuildTitle = Metabuild.class.getPackage().getImplementationTitle();
		this.metabuildVersion = Metabuild.class.getPackage().getImplementationVersion();
		if (this.metabuildTitle == null) this.metabuildTitle = "N/A";
		if (this.metabuildVersion == null) this.metabuildVersion = "N/A";
		System.setProperty(META_TITLE_PROPERTY, this.metabuildTitle);
		System.setProperty(META_VERSION_PROPERTY, this.metabuildVersion);
		
		// Set meta bin directory
		try {
			String metaHome = Metabuild.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			System.setProperty(META_HOME_PROPERTY, new File(metaHome).getParent());
		} catch (NullPointerException e) {
			logger().error("failed to access meta home directory!", e);
		}
	}
	
	@Override
	public MetaState getState() {
		return this.phase;
	}
	
	@Override
	public void close() {
		try {
			this.pluginLoader.close();
		} catch (IOException e) {
			logger().errort(LOG_TAG, "unable to close plugin class loader", e);
		}
		if (this.logStream != null) {
			try {
				this.logStream.close();
			} catch (IOException e) {}
			this.logFile = null;
			this.logger = new StreamLogger(OutputStream.nullOutputStream());
		}
		this.task2node.clear();
		this.taskDependencies.clear();
		this.sourceIncludes.clear();
		this.taskTree = null;
		this.buildstack.clear();
		stateTransition(MetaState.IDLE, MetaState.values());
	}
	
	private void stateTransition(MetaState to, MetaState... from) {
		if (to == MetaState.ERROR) {
			logger().debugt(LOG_TAG, "STATE TRANSITION: %s >>> %s", this.phase.name(), to.name());
			logger().debugt(LOG_TAG, "UNRECOVERABLE ERROR TERMINATION REQUIRED");
			this.phase = MetaState.ERROR;
			return;
		}
		if (this.phase == to) {
			logger().debugt(LOG_TAG, "STATE NO TRANSITION: %s", this.phase.name());
			return;
		}
		for (MetaState s : from)
			if (s == this.phase) {
				logger().debugt(LOG_TAG, "STATE TRANSITION: %s >>> %s", this.phase.name(), to.name());
				this.phase = to;
				return;
			}
		logger().debugt(LOG_TAG, "INVALID STATE TRANSITION: %s >X> %s", this.phase.name(), to.name());
		this.phase = MetaState.ERROR;
		logger().debugt(LOG_TAG, "STATE ERROR TERMINATION REQUIRED");
		throw BuildScriptException.msg("Illegal state transition error: %s to %s", this.phase.name(), to.name());
	}
	
	@Override
	public void terminate() {
		if (instance != null) {
			if (instance != this)
				throw MetaInitError.msg("termination call on non active instance of metabuild!");
			close();
		}
		instance = null;
	}

	@Override
	public void setCacheDirectory(File cacheDirectory) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		this.cacheDirectory = FileUtility.absolute(cacheDirectory, workingDir());
	}

	/**
	 * @param logFile The log file to write to
	 */
	@Override
	public void setLogFile(File logFile) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		this.logFile = logFile;
	}
	
	public File getLogFile() {
		return logFile;
	}
	
	@Override
	public void setRefreshDependencies(boolean refreshDependencies) {
		this.refreshDependencies = refreshDependencies;
	}
	
	public boolean isRefreshDependencies() {
		return refreshDependencies;
	}
	
	@Override
	public void setSkipTaskRun(boolean skipTaskRun) {
		this.skipTaskRun = skipTaskRun;
	}
	
	public boolean isSkipTaskRun() {
		return skipTaskRun;
	}
	
	@Override
	public void setForceRunTasks(boolean forceRunTasks) {
		this.forceRunTasks = forceRunTasks;
	}
	
	public boolean isForceRunTasks() {
		return forceRunTasks;
	}
	
	@Override
	public void setTaskThreads(int taskThreads) {
		if (taskThreads <= 0) throw new IllegalArgumentException("number of threads must be >= 1!");
		this.taskThreads = taskThreads;
	}

	@Override
	public void addStatusCallback(IStatusCallback statusCallback) {
		this.statusCallback.add(statusCallback);
	}

	@Override
	public void setTerminalOutput(PrintStream print, boolean printUI) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		this.outputHandler = new OutputHandler(this, print, printUI);
	}
	
	public void setConsoleInputTarget(OutputStream targetStream) {
		// set new writer which prints to the target stream (or remove old one if null)
		this.consoleStreamTarget = targetStream == null ? null : new PrintWriter(targetStream);

		// if new target set and currently now pipe worker running, start new worker
		if (this.consoleStreamTarget != null && this.consolePipeClosed) {
			ForkJoinPool.commonPool().execute(() -> {
				try {
					// read all garbage that might be stuck in the input buffer
					this.consoleStream.readNBytes(this.consoleStream.available());
					
					// update pipe flag to indicate worker running
					this.consolePipeClosed = false;
					// pipe console input from input stream to output writer
					BufferedReader source = new BufferedReader(new InputStreamReader(this.consoleStream));
					String line;
					while ((line = source.readLine()) != null) {
						this.consoleStreamTarget.println(line);
						this.consoleStreamTarget.flush();
					}
					this.consoleStreamTarget.close();
				} catch (Throwable e) {}
				// update pipe flag to indicate worker terminated
				this.consolePipeClosed = true;
			});
		}
	}

	@Override
	public void setConsoleStreamInput(InputStream input) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		if (input == null) {
			this.consoleStream = InputStream.nullInputStream();
		} else {
			this.consoleStream = input;
		}
	}
	
	@Override
	public void setLogStreamOutput(Object output) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		if (output instanceof OutputStream logStream) {
			this.terminalLogger = new StreamLogger(logStream, StandardCharsets.UTF_8, true);
		} else if (output instanceof Logger logger) {
			this.terminalLogger = logger;
		} else if (output == null) {
			this.terminalLogger = null;
		} else {
			throw new IllegalArgumentException("Terminal logger must be null or a subclass of either OutputStream or Logger!");
		}
	}
	
	/**
	 * @return The current metabuild instance
	 */
	public static Metabuild get() {
		if (instance == null) throw MetaInitError.msg("metabuild instance not yet created in this VM!");
		return instance;
	}
	
	@Override
	public String getMetabuildVersion() {
		return metabuildVersion;
	}

	@Override
	public String getMetabuildTitle() {
		return metabuildTitle;
	}
	
	public Logger logger() {
		return this.logger != null ? this.logger : Log.defaultLogger();
	}
	
	/**
	 * Register a new task to the current metabuild instance.<br>
	 * This method should only be called trough task creation from the build script.
	 * @param task The task to register
	 * @return true if and only if the task was not already registered, has a valid name, and could be registered
	 */
	public boolean registerTask(BuildTask task) {
		BuildScript buildscript = peekBuild();
		task.setBuildscript(buildscript);
		String fullTaskName = task.fullName();
		if (getState() != MetaState.INIT)
			throw BuildScriptException.msg("attempt to register task outside INIT phase: %s", fullTaskName);
		if (!TASK_NAME_FILTER.matcher(fullTaskName).matches()) {
			logger().warnt(LOG_TAG, "task name '%s' is invalid, needs to match %s!", fullTaskName, IMeta.TASK_NAME_FILTER);
			return false;
		}
		if (this.registeredTasks.containsKey(fullTaskName)) {
			logger().warnt(LOG_TAG, "task '%s' already registered!", fullTaskName);
			return false;
		}
		this.registeredTasks.put(fullTaskName, task);
		return true;
	}
	
	/**
	 * Adds includes that define dependencies required for the development environment by the the current tasks.<br>
	 * Mainly dependencies that are required to be present in the IDE.
	 * @param includes The dependencies to add to the includes wrapped in an language specific class
	 */
	public void addSourceInclude(ISourceIncludes includes) {
		if (getState() != MetaState.FINISH)
			throw BuildScriptException.msg("attempt to add source includes outside FINISH phase!");
		this.sourceIncludes.add(includes);
	}
	
	/**
	 * Attempts to find the build script imported with the given name.
	 * @param importName The name under which the build file was imported
	 * @return The build script instance created from the imported build file
	 */
	public BuildScript buildNamed(String importName) {
		if (this.importAlias.containsKey(importName))
			importName = this.importAlias.get(importName);
		if (!this.imports.containsKey(importName)) {
			throw BuildScriptException.msg("no build file named '%s' is registered!", importName);
		}
		return this.imports.get(importName);
	}
	
	/**
	 * Attempts to find and return the task with the requested name.
	 * @param name The name of the task
	 * @return The task with the name or null if none was found
	 */
	public BuildTask taskNamed(String name) {
		if (!name.contains(":"))
			name = peekBuild().buildName + ":" + name;
		else {
			int i = name.indexOf(':');
			String buildName = name.substring(0, i);
			if (this.importAlias.containsKey(buildName))
				name = this.importAlias.get(buildName) + ":" + name.substring(i + 1);
		}
		if (!this.registeredTasks.containsKey(name)) {
			throw BuildScriptException.msg("no task named '%s' is registered!", name);
		}
		return this.registeredTasks.get(name);
	}
	
	/**
	 * Registers new dependencies for the task, does not change anything if the dependencies where already registered for the task.
	 * @param task The task to add dependencies to
	 * @param dependencies The dependencies to add
	 */
	public void taskDepend(BuildTask task, BuildTask... dependencies) {
		if (getState() != MetaState.INIT)
			throw BuildScriptException.msg("attempt to register task dependency outside INIT phase!");
		if (task == null) return;
		Set<String> dep = this.taskDependencies.get(task.fullName());
		if (dep == null) this.taskDependencies.put(task.fullName(), dep = new HashSet<>());
		dep.addAll(Stream.of(dependencies).filter(t -> t != null).map(t -> t.fullName()).toList());
	}
	
	@Override
	public <T> void getTasks(T ref, Collection<MetaGroup<T>> groups, Collection<MetaTask<T>> tasks) {
		this.registeredTasks.values().forEach(t -> {
			if (!t.buildscript().buildName.isEmpty()) return; // only return root tasks
			if (t.group != null) {
				Optional<MetaGroup<T>> group = groups.stream().filter(g -> g.group().equals(t.group)).findAny();
				if (group.isEmpty()) {
					group = Optional.of(new MetaGroup<>(ref, t.group));
					groups.add(group.get());
				}
				tasks.add(new MetaTask<>(ref, group, t.fullName()));
			} else {
				tasks.add(new MetaTask<>(ref, Optional.empty(), t.fullName()));
			}
		});
	}
	
	@Override
	public void getSourceIncludes(Collection<ISourceIncludes> includes) {
		includes.addAll(this.sourceIncludes);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T extends ISourceIncludes> void getSourceIncludes(Collection<T> includes, String language) {
		for (var source : this.sourceIncludes) {
			if (source.languageId().equals(language)) {
				includes.add((T) source);
			}
		}
	}
	
	private Optional<String> checkPlugin(File pluginFile) {
		try {
			JarFile jar = new JarFile(pluginFile);
			Attributes attributes = jar.getManifest().getMainAttributes();
			String pluginName = attributes.getValue("Metabuild-Plugin-Name");
			String versionName = attributes.getValue("Implementation-Version");
			jar.close();
			return pluginName != null ? Optional.of(String.format("(%s) %s", pluginName, versionName)) : Optional.empty();
			
		} catch (IOException e) {
			return Optional.empty();
		}
	}
	
	/**
	 * Initialized directories and files such as the cache directory and the log file and ensures the instance to be in IDLE state
	 * @return true if and only if all necessary directories and files could be created and the meta instacne if now in IDLE state
	 */
	public boolean preInit() {
		if (this.phase.isRunning()) {
			close();
			return true;
		}
		
		if (this.phase == MetaState.IDLE) return true;
		
		if (this.workingDirectory == null) setWorkingDirectory(DEFAULT_CACHE_DIRECTORY);
		if (this.cacheDirectory == null) setCacheDirectory(DEFAULT_CACHE_DIRECTORY);
		
		if (!this.cacheDirectory.isDirectory() && !this.cacheDirectory.mkdir()) {
			logger().errort(LOG_TAG, "could not create cache directory: %s", this.cacheDirectory.getPath());
			return false;
		}
		if (this.logger == null) {
			try {
				if (this.logFile != null)
					this.logFile = FileUtility.absolute(logFile, workingDir());
				if (this.logFile == null || (!this.logFile.getParentFile().isDirectory() && !this.logFile.getParentFile().mkdirs())) {
					if (this.logFile != null)
						logger().warnt(LOG_TAG, "failed to create log file directory!");
					if (this.terminalLogger != null)
						this.logger = new StacktraceLogger(this.terminalLogger);
				} else {
					this.logStream = new FileOutputStream(this.logFile);
					if (this.terminalLogger != null) {
						this.logger = new StacktraceLogger(new MultiLogger(new StreamLogger(logStream), this.terminalLogger));
					} else {
						this.logger = new StacktraceLogger(new StreamLogger(logStream));
					}
				}
			} catch (FileNotFoundException e) {
				logger().warnt(LOG_TAG, "failed to create log file: " + e.getMessage());
				this.logger = new StacktraceLogger(this.terminalLogger);
			}
			
			if (this.logger == null) this.logger = new StreamLogger(OutputStream.nullOutputStream());
			
			this.metaHome = new File(System.getProperty(META_HOME_PROPERTY));
			
			// Print version info to log
			logger().debug("JVM runtime: %s", System.getProperty("java.version"));
			logger().debug("Meta runtime: %s", System.getProperty(META_VERSION_PROPERTY));
			logger().debug("Meta home: %s", this.metaHome);
		}
		
		// load system plugins
		File[] pluginFiles = this.metaHome.listFiles();
		if (pluginFiles != null) {
			logger().infot(LOG_TAG, "search system plugins: %s", this.metaHome);
			for (File pluginFile : pluginFiles) {
				if (!FileUtility.getExtension(pluginFile).equalsIgnoreCase("jar")) continue;
				Optional<String> pluginInfo = checkPlugin(pluginFile);
				if (pluginInfo.isEmpty()) continue;
				this.pluginLoader.addFile(pluginFile);
				logger().infot(LOG_TAG, "- %s %s", pluginFile.getName(), pluginInfo.get());
			}
		}
		stateTransition(MetaState.IDLE, MetaState.PREINIT);
		return true;
	}
	
	@Override
	public void setWorkingDirectory(File workingDirectory) {
		if (getState() != MetaState.PREINIT)
			throw new IllegalStateException("configurations can only be changed in PREINIT phase!");
		this.workingDirectory = workingDirectory;
	}
	
	@Override
	public Collection<File> getBuildfileClasspath() {
		List<File> classpathFiles = new ArrayList<File>();
		classpathFiles.add(new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile()));
		classpathFiles.addAll(pluginLoader.getFiles());
		return classpathFiles;
	}
	
	public File buildWorkingDir() {
		BuildScript buildscript = peekBuild();
		if (buildscript == null) return workingDir();
		return FileUtility.absolute(buildscript.buildfileLocation.getParentFile(), this.workingDirectory);
	}

	@Override
	public File workingDir() {
		return this.workingDirectory;
	}
	
	@Override
	public File metaHome() {
		return this.metaHome;
	}

	@Override
	public File cacheDir() {
		return this.cacheDirectory;
	}

	/**
	 * @return The compiler used to compile build file scripts.
	 */
	public ScriptCompiler getBuildCompiler() {
		return buildCompiler;
	}
	
	@Override
	public boolean initBuild(File buildFile) {
		if (!preInit()) return false;
		
		stateTransition(MetaState.INIT, MetaState.IDLE, MetaState.READY);
		this.registeredTasks.clear();

		buildFile = FileUtility.absolute(buildFile, workingDir());
		if (!buildFile.isFile()) {

			// Initialize dummy build script to allow call to built in tasks
			this.imports.put("", new BuildScript());
			pushBuild("");
			peekBuild().buildName = "";
			peekBuild().buildfileLocation = new File(workingDir(), "builtin");
			peekBuild().init(); // should never fail
			RootTask.TASK.setBuildscript(peekBuild());
			popBuild();
			
		} else {

			try {
				importBuild("", this.workingDirectory, buildFile);
			} catch (MetaScriptException e) {
				logger().errort(LOG_TAG, "buildfile init phase failed!");
				e.printStack(logger().errorPrinter(LOG_TAG));
				stateTransition(MetaState.IDLE, MetaState.INIT);
				return false;
			} catch (Throwable e) {
				logger().errort(LOG_TAG, "buildfile init threw uncatched exception:", e);
				stateTransition(MetaState.ERROR);
				return false;
			}
			
		}
		
		stateTransition(MetaState.READY, MetaState.INIT);
		return true;
	}

	public BuildScript loadBuildfile(File buildFile) {
		if (getState() != MetaState.INIT) {
			logger().errort(LOG_TAG, "attempt to load buildfile outside INIT phase: %s", buildFile);
			return null;
		}
		
		BuildScript buildscript = this.buildCompiler.loadBuildFile(buildFile, this.pluginLoader);
		if (buildscript == null) {
			logger().errort(LOG_TAG, "failed to load buildfile, build aborted!");
			return null;
		}
		
		logger().infot(LOG_TAG, "loaded buildfile: %s", buildFile);
		
		return buildscript;
	}
	
	public BuildScript asyncEnterBuild(String name) {
		synchronized (this.buildstack) {
			BuildScript buildscript = peekBuild();
			while (buildscript != null && !buildscript.buildName.equals(name)) {
				try { this.buildstack.wait(); } catch (InterruptedException e) {}
			}
			return pushBuild(name);
		}
	}
	
	public void asyncLeaveBuild() {
		synchronized (this.buildstack) {
			popBuild();
			this.buildstack.notifyAll();
		}
	}
	
	public BuildScript pushBuild(String name) {
		BuildScript imp = this.imports.get(name);
		if (imp == null) throw BuildScriptException.msg("attempt to access not imported build: %s", name);
		this.buildstack.push(imp);
		return imp;
	}
	
	public void popBuild() {
		if (this.buildstack.pop() == null)
			throw BuildScriptException.msg("build stack underflow error!");
	}
	
	public BuildScript peekBuild() {
		return this.buildstack.peek();
	}
	
	public void importBuild(String importName, File location, File buildFile) {
		if (location == null && buildFile != null)
			location = buildFile.getParentFile();
		if (buildFile == null && location != null)
			buildFile = FileUtility.concat(location, IMeta.DEFAULT_BUILD_FILE_NAME);
		location = FileUtility.absolute(location);
		buildFile = FileUtility.absolute(buildFile);
		if (importName == null)
			importName = location.getParentFile().getName();
		if (this.imports.containsKey(importName)) return;
		
		// detect duplicates and create alias
		File f_buildFile = buildFile;
		String duplicateName = this.imports.entrySet().stream()
				.filter(e -> e.getValue().buildfileLocation.equals(f_buildFile))
				.map(Map.Entry::getKey).findAny().orElse(null);
		if (duplicateName != null) {
			this.importAlias.put(importName, duplicateName);
			return;
		}
		
		// import build file
		if (!buildFile.isFile()) {
			throw BuildScriptException.msg("imported '%s' build file does not exist: %s", importName, buildFile);
		}
		BuildScript buildscript = Metabuild.get().loadBuildfile(buildFile);
		if (buildscript == null) {
			throw BuildScriptException.msg("failed to load import '%s' build file: %s", importName, buildFile);
		}
		buildscript.buildName = importName;
		buildscript.buildfileLocation = buildFile;
		this.imports.put(importName, buildscript);
		if (importName.isEmpty()) RootTask.TASK.setBuildscript(buildscript);
		
		// load project plugins
		File[] pluginFiles = new File(location, META_PROJECT_PLUGIN_LOCATION).listFiles();
		if (pluginFiles != null) {
			logger().infot(LOG_TAG, "load project plugins: %s", location);
			for (File pluginFile : pluginFiles) {
				if (!FileUtility.getExtension(pluginFile).equalsIgnoreCase("jar")) continue;
				if (this.pluginLoader.getFiles().contains(buildFile)) continue;
				Optional<String> pluginInfo = checkPlugin(pluginFile);
				if (pluginInfo.isEmpty()) continue;
				this.pluginLoader.addFile(pluginFile);
				logger().infot(LOG_TAG, "- %s %s", pluginFile.getName(), pluginInfo.get());
			}
		}
		
		// run init on buildfile to register tasks
		try {
			pushBuild(importName);
			buildscript.init();
			popBuild();
		} catch (MetaScriptException e) {
			throw BuildScriptException.msg(e, "unable to initialize import '%s' build file!", importName);
		}
	}
	
	/**
	 * Represents an build task and its dependencies that have to be executed
	 */
	public static record TaskNode(Optional<BuildTask> task, Set<TaskNode> dep) { }
	
	/**
	 * Checks if an task has to be executed and if so, prepares it to be executed.<br>
	 * Also performs the same operations for all registered dependencies of that task.
	 * @param taskName The name of the task to prepare
	 */
	protected void prepareTask(String taskName, StackList<String> taskTrace) {
		
		BuildTask task = taskNamed(taskName.contains(":") ? taskName : ":" + taskName);
		if (task == null) {
			throw BuildScriptException.msg("task '%s' does not exist", taskName);
		}
		
		Set<String> dependencies = this.taskDependencies.get(task.fullName());
		Set<TaskNode> dependendNodes = new HashSet<>();
		if (dependencies != null) {
			for (String depTask : dependencies) {
				try {
					if (taskTrace.contains(taskName))
						throw BuildScriptException.msg("recursive task dependency detected: %s", taskTrace.toString());
					taskTrace.push(taskName);
					prepareTask(depTask, taskTrace);
					taskTrace.pop();
				} catch (MetaScriptException e) {
					throw BuildScriptException.msg(e, "problem with task '%s' required by '%s'", depTask, taskName);
				}
				if (this.taskTree != null) dependendNodes.add(this.taskTree);
			}
		}
		
		this.taskTree = this.task2node.get(task);
		if (this.taskTree != null) return;
		
		pushBuild(task.buildscript().buildName);
		
		try {
			TaskState state = task.state(); // make ABSOLUTELY sure it is called at least once
			if (!state.requiresBuild() && dependendNodes.isEmpty() && !this.forceRunTasks) {
				this.taskTree = null;
				return;
			}
		} catch (MetaScriptException e) {
			throw BuildScriptException.msg(e, "failed to query state for task: %s", task.fullName());
		} finally {
			popBuild();
		}
		
		this.taskTree = new TaskNode(Optional.of(task), dependendNodes);
		this.task2node.put(task, this.taskTree);
		
	}
	
	/**
	 * Checks which tasks are required to complete the requested tasks and prepares them for execution.<br>
	 * Build a tree of tasks and their dependencies that can then be executed.
	 * @param tasks The list of tasks to execute.
	 * @return true if and only if all tasks where prepared for execution successfully.
	 */
	private boolean buildTaskTree(List<String> tasks) {
		
		try {

			this.task2node.clear();
			this.taskTree = null;
			
			Set<TaskNode> dependencies = new HashSet<>();
			
			for (String task : tasks) {
				try {
					prepareTask(task, new StackList<String>());
				} catch (MetaScriptException e) {
					logger().errort(LOG_TAG, "failed to build task tree for task: %s", task);
					e.printStack(logger().errorPrinter(LOG_TAG));
					return false;
				}
				if (this.taskTree != null) dependencies.add(this.taskTree);
			}
			
			this.taskTree = new TaskNode(Optional.of(RootTask.TASK), dependencies);
			return true;
			
		} catch (MetaScriptException e) {
			logger().errort(LOG_TAG, "failed to prepare all tasks:");
			e.printStack(logger().errorPrinter(LOG_TAG));
			stateTransition(MetaState.READY, MetaState.PREPARE);
			return false;
		} catch (Throwable e) {
			logger().errort(LOG_TAG, "uncatched exception while building task tree:", e);
			stateTransition(MetaState.READY, MetaState.PREPARE);
			return false;
		}
	}
	
	@Override
	public void abortTasks() {
		this.doAbort = true;
		if (this.taskExecutor == null) return;
		synchronized (this.taskExecutor) {
			this.taskExecutor.notifyAll();
		}
	}
	
	/**
	 * Executes the prepared task tree, from the specified node upwards.
	 * @param node The node in the task tree to run
	 * @return true if and only if all the tasks from the node upward have completed successfully
	 */
	private CompletableFuture<Void> runTaskTree(TaskNode node, Map<TaskNode, CompletableFuture<Void>> taskFutures) {
		
		return CompletableFuture.allOf(
			node.dep().stream().map(task -> {
				CompletableFuture<Void> future = taskFutures.get(task);
				if (future == null)
					taskFutures.put(task, (future = runTaskTree(task, taskFutures)));
				return future;
			}).toArray(CompletableFuture[]::new)
		).thenRunAsync(() -> {
			if (this.doAbort) return; // do not start any more tasks, we are aborting the build
			if (node.task().isEmpty()) return;
			asyncEnterBuild(node.task().get().buildscript().buildName);
			try {
				if (this.statusCallback != null) this.statusCallback.forEach(s -> s.taskStarted(node.task().get().fullName()));
				boolean result = node.task().get().runTask(
						status -> statusCallback.forEach(s -> s.taskStatus(node.task().get().fullName(), status)));
				if (this.doAbort) return; // do not further check results, the task was aborted
				if (this.statusCallback != null) this.statusCallback.forEach(s -> s.taskCompleted(node.task().get().fullName()));
				if (!result) {
					throw BuildException.msg("task '%s' failed!", node.task().get().fullName());
				}
			} finally {
				asyncLeaveBuild();
			}
		}, this.taskExecutor);
		
	}
	
	@Override
	public boolean runTasks(String... tasks) {
		return runTasks(Arrays.asList(tasks));
	}
	
	/**
	 * Prints a short info about the number of tasks registered, and how many of them are up-to-date or failed
	 */
	private void printStatus() {
		
		int upToDate = 0;
		int	failed = 0;
		int taskCount = 0;
		for (BuildTask task : this.registeredTasks.values()) {
			if (!task.didRun()) continue;
			if (task.state() == TaskState.UPTODATE) upToDate++;
			if (task.state() == TaskState.FAILED) failed++;
			taskCount++;
		}
		
		logger().infot(LOG_TAG, "TASKS: %d  UP_TO_DATE: %s  FAILED: %d", taskCount, upToDate, failed);
		
	}
	
	@Override
	public boolean runTasks(List<String> tasks) {
		
		this.doAbort = false;
		
		/* Prepare tasks for execution, check which ones have to run and build the task tree to execut
		 * The actual code that runs in the prepare phase usualy does not take very long to cmoplete
		 * but it still might take a bit to complete.
		 */
		
		stateTransition(MetaState.PREPARE, MetaState.READY);
		if (!buildTaskTree(tasks)) {
			logger().errort(LOG_TAG, "could not build task tree, abort build!");

			this.refreshDependencies = false;
			this.forceRunTasks = false;
			this.skipTaskRun = false;
			return false;
		}
		
		if (this.doAbort) {
			logger().errort(LOG_TAG, "build was aborted");
			stateTransition(MetaState.READY, MetaState.PREPARE);
			return false;
		}
		
		if (this.statusCallback != null) this.statusCallback.forEach(s -> s.taskCount(this.task2node.size()));
		
		/* Actually run the tasks (uncles this phase is requested to be skipped)
		 * This might take some very long time depending on the amount of work to complete.
		 * An abort call while still in this phase causes an abort request to be send to each running task.
		 * Tasks should terminate as soon as possible when receiving this request, even if this means leaving their work incomplete. 
		 * 
		 * If one or more tasks do not respond within the timeout, the build system is forcefully terminated, leaving it in an state
		 * in which it can potentially not continue to process further requests, an complete reset is required.
		 */
		
		boolean success = false;
		if (!this.skipTaskRun) {
			
			if (this.taskTree.dep().stream().filter(n -> n.task().isPresent()).count() == 0) {
				logger().infot(LOG_TAG, "nothing to do");
				success = true;
			}
			
			this.taskQueue = new ArrayBlockingQueue<>(this.registeredTasks.size());
			this.taskExecutor = new ThreadPoolExecutor(0, this.taskThreads, 10, TimeUnit.SECONDS, this.taskQueue);
			
			try {
				stateTransition(MetaState.RUN, MetaState.PREPARE);
				CompletableFuture<Void> buildTask = runTaskTree(this.taskTree, new HashMap<Metabuild.TaskNode, CompletableFuture<Void>>());
				
				// wait for completition
				while (!buildTask.isDone()) {
					
					try {
						synchronized (this.taskExecutor) {
							this.taskExecutor.wait(1000);
						}
					} catch (InterruptedException e1) {
						stateTransition(MetaState.ERROR);
						return false;
					}
					
					if (this.doAbort) {
						logger().warnt(LOG_TAG, "ABORT OF ALL TASKS REQUESTED");
						// notify all running tasks to terminate as soon as possible
						Queue<TaskNode> toNotify = new ArrayDeque<Metabuild.TaskNode>();
						toNotify.add(this.taskTree);
						while (toNotify.size() > 0) {
							TaskNode task = toNotify.poll();
							toNotify.addAll(task.dep());
							if (task.task().isPresent())
								task.task().get().abortTask();
						}
						// await task termination
						try {
							buildTask.orTimeout(ABORT_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
						} catch (CompletionException e) {
							if (e.getCause() instanceof TimeoutException) {
								// timeout, tasks stuck in running state and not responding
								logger().errort(LOG_TAG, "tasks did not react to abort request, force terminating ...");

								// abort execution of any more tasks, the task tree will not be able to finish normally any more
								this.taskExecutor.shutdownNow();
								// enter error state, further executions are not possible, system has to be terminated
								stateTransition(MetaState.ERROR);
								return false;
							}
							throw e;
						}
					}
					
				}
				buildTask.join();
				
				success = !this.doAbort;
			} catch (CompletionException e)  {
				if (e.getCause() instanceof MetaScriptException me) {
					logger().errort(LOG_TAG, "build task error:");
					me.printStack(logger().errorPrinter(LOG_TAG));
				} else {
					logger().errort(LOG_TAG, "uncatched build task error:", e.getCause());
					stateTransition(MetaState.ERROR);
					return false;
				}
			}
			
		} else {
			logger().infot(LOG_TAG, "skipping build run phase");
			success = true;
		}

		stateTransition(MetaState.FINISH, MetaState.PREPARE, MetaState.RUN);
		
		/**
		 * Execute post-task-work, like suppling information about the build to external processes.
		 */
		
		if (success) {
			try {
				pushBuild("").finish();
			} catch (MetaScriptException e) {
				logger().errort(LOG_TAG, "buildfile finish phase failed!");
				e.printStack(logger().errorPrinter(LOG_TAG));
			} catch (Throwable e) {
				logger().errort(LOG_TAG, "buildfile finish threw uncatched exception:", e);

				this.refreshDependencies = false;
				this.forceRunTasks = false;
				this.skipTaskRun = false;
				stateTransition(MetaState.ERROR);
				return false;
			} finally {
				popBuild();
			}
		}

		if (this.doAbort) {
			logger().errort(LOG_TAG, "build was aborted");
			stateTransition(MetaState.READY, MetaState.FINISH);
			return false;
		}
		
		stateTransition(MetaState.SHUTDOWN, MetaState.FINISH);
		
		/**
		 * Call cleanup on all tasks and unload all resources.
		 */
		
		this.registeredTasks.values().forEach(BuildTask::cleanupTask);
	 
		try {
			if (this.taskExecutor != null) {
				if (!this.taskExecutor.shutdownNow().isEmpty()) {
					logger().warnt(LOG_TAG, "tasks still runing after build, this indicates sirious problems with the build file!");
					logger().warnt(LOG_TAG, "attempt force termination of remaining tasks ...");
				}
				if (!this.taskExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
					logger().warnt(LOG_TAG, "failed to shutdown build tasks, executor not responding!");
				}
				this.taskQueue.clear();
				this.taskExecutor = null;
				this.taskQueue = null;
			}
		} catch (InterruptedException e) {}
		
		stateTransition(MetaState.READY, MetaState.SHUTDOWN);
		printStatus();
		
		for (var s : this.statusCallback) s.buildCompleted(success);

		this.refreshDependencies = false;
		this.forceRunTasks = false;
		this.skipTaskRun = false;
		this.doAbort = false;
		
		return success;
		
	}
	
}
