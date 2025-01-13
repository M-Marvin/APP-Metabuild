package de.m_marvin.metabuild.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.MetaGroup;
import de.m_marvin.metabuild.api.core.MetaTask;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaInitError;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.compile.ScriptCompiler;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.script.BuildScript;
import de.m_marvin.metabuild.tasks.BuildTask;
import de.m_marvin.metabuild.tasks.BuildTask.TaskState;
import de.m_marvin.metabuild.tasks.RootTask;
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
	/* Cache directory for metadata and downloaded files, normall user directory */
	private File cacheDirectory;
	/* Log file to store logging output */
	private File logFile;
	/* OutputStream to the log file */
	private OutputStream logStream;
	/* Root logger, all loggers end up here */
	private Logger logger;
	/* If the logger output is printed to the terminal */
	private Logger terminalLogger = new SystemLogger();
	/* Number of allowed tasks to spawn for processing tasks in parallel */
	private int taskThreads;
	/* Set to true if the next build process should re-download all external dependencies */
	private boolean refreshDependencies = false;
	/* Currently loaded build script instance */
	private BuildScript buildscript;
	/* Task to TaskNode map, nodes combine one BuildTask and its dependent tasks */
	private Map<BuildTask, TaskNode> task2node = new HashMap<>();
	/* Root TaskNode for the current build task tree */
	private TaskNode taskTree;
	/* Queue of build tasks currently executed by the task threads */
	private BlockingQueue<Runnable> taskQueue;
	/* Executor for build tasks */
	private ThreadPoolExecutor taskExecutor;
	/* Compiler for loading and instantiating build script */
	private final ScriptCompiler buildCompiler;
	/* Map of registered tasks of the current build script */
	private final Map<String, BuildTask> registeredTasks = new HashMap<>();
	/* Map of registered task dependencies of current build script */
	private final Map<String, Set<String>> taskDependencies = new HashMap<>();
	/* Status callback to report back build progress */
	private IStatusCallback statusCallback;
	
	/**
	 * Instantiates a new metabuild instance.<br>
	 * Only one instance can be created in the runtime environment at a time
	 * @param workingDirectory The working directory of the instance, usual the directory containing the projects build file
	 */
	public Metabuild() {
		if (instance != null) throw MetaInitError.msg("can't instantiate multiple metabuild instances in same VM!");
		instance = this;
		
		setTaskThreads(DEFAULT_TASK_THREADS);
		
		this.buildCompiler = new ScriptCompiler(this);
		
		// Set meta properties
		String titleInfo = Metabuild.class.getPackage().getImplementationTitle();
		String versionInfo = Metabuild.class.getPackage().getImplementationVersion();
		System.setProperty(META_TITLE_PROPERTY, titleInfo);
		System.setProperty(META_VERSION_PROPERTY, versionInfo);
		
		// Set meta bin directory
		try {
			String metaHome = Metabuild.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			System.setProperty(META_HOME_PROPERTY, new File(metaHome).getParent());
		} catch (NullPointerException e) {
			logger().error("failed to access meta home directory!", e);
		}
	}
	
	@Override
	public void close() {
		if (this.logStream != null) {
			try {
				this.logStream.close();
			} catch (IOException e) {}
			this.logFile = null;
		}
		this.task2node.clear();
		this.taskDependencies.clear();
		this.taskTree = null;
		this.buildscript = null;
	}
	
	@Override
	public void terminate() {
		if (instance != null) {
			if (instance != this)
				throw MetaInitError.msg("termination call on non active instance of metabuild!");
			instance.close();
		}
		instance = null;
	}

	@Override
	public void setCacheDirectory(File cacheDirectory) {
		this.cacheDirectory = FileUtility.absolute(cacheDirectory);
	}

	/**
	 * @param logFile The log file to write to
	 */
	@Override
	public void setLogFile(File logFile) {
		this.logFile = FileUtility.absolute(logFile);
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
	public void setTaskThreads(int taskThreads) {
		if (taskThreads <= 0) throw new IllegalArgumentException("number of threads must be >= 1!");
		this.taskThreads = taskThreads;
	}

	@Override
	public void setStatusCallback(IStatusCallback statusCallback) {
		this.statusCallback = statusCallback;
	}

	@Override
	public void setTerminalOutput(Object output) {
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
		if (!TASK_NAME_FILTER.matcher(task.name).matches()) {
			logger().warnt(LOG_TAG, "Task name '%s' is invalid, needs to match [\\d\\w]+!", task.name);
			return false;
		}
		if (this.registeredTasks.containsKey(task.name)) {
			logger().warnt(LOG_TAG, "Task '%s' already registered!", task.name);
			return false;
		}
		this.registeredTasks.put(task.name, task);
		return true;
	}
	
	/**
	 * Attempts to find and return the task with the requested name.
	 * @param name The name of the task
	 * @return The task with the name or null if none was found
	 */
	public BuildTask taskNamed(String name) {
		if (!this.registeredTasks.containsKey(name)) {
			logger.warnt(LOG_TAG, "No task named '%s' is registered!", name);
			return null;
		}
		return this.registeredTasks.get(name);
	}
	
	/**
	 * Registers new dependencies for the task, does not change anything if the dependencies where already registered for the task.
	 * @param task The task to add dependencies to
	 * @param dependencies The dependencies to add
	 */
	public void taskDepend(BuildTask task, BuildTask... dependencies) {
		if (task == null) return;
		Set<String> dep = this.taskDependencies.get(task.name);
		if (dep == null) this.taskDependencies.put(task.name, dep = new HashSet<>());
		dep.addAll(Stream.of(dependencies).filter(t -> t != null).map(t -> t.name).toList());
	}
	
	@Override
	public <T> void getTasks(T ref, List<MetaGroup<T>> groups, List<MetaTask<T>> tasks) {
		this.registeredTasks.values().forEach(t -> {
			if (t.group != null) {
				Optional<MetaGroup<T>> group = groups.stream().filter(g -> g.group().equals(t.group)).findAny();
				if (group.isEmpty()) {
					group = Optional.of(new MetaGroup<>(ref, t.group));
					groups.add(group.get());
				}
				tasks.add(new MetaTask<>(ref, group, t.name));
			} else {
				tasks.add(new MetaTask<>(ref, Optional.empty(), t.name));
			}
		});
	}
	
	/**
	 * Initialized directories and files such as the cache directory and the log file
	 * @return true if and only if all necessary directories and files could be created
	 */
	public boolean initDirectories() {
		close();
		
		if (this.workingDirectory == null) setWorkingDirectory(new File(DEFAULT_CACHE_DIRECTORY));
		if (this.cacheDirectory == null) setCacheDirectory(new File(DEFAULT_CACHE_DIRECTORY));
		if (this.logFile == null) setLogFile(new File(DEFAULT_BUILD_LOG_NAME));
		
		if (!this.cacheDirectory.isDirectory() && !this.cacheDirectory.mkdir()) {
			logger().errort(LOG_TAG, "could not create cache directory: %s", this.cacheDirectory.getPath());
			return false;
		}
		if (this.logger == null) {
			try {
				if (!this.logFile.getParentFile().isDirectory() && !this.logFile.getParentFile().mkdirs()) {
					logger().warnt(LOG_TAG, "failed to create log file directory!");
					if (this.terminalLogger != null) this.logger = new StacktraceLogger(this.terminalLogger);
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
			
			// Print version info to log
			logger().debug("JVM runtime: %s", System.getProperty("java.version"));
			logger().debug("Meta runtime: %s", System.getProperty(META_VERSION_PROPERTY));
			logger().debug("Meta home: %s", System.getProperty(META_HOME_PROPERTY));
		}
		return true;
	}
	
	@Override
	public void setWorkingDirectory(File workingDirectory) {
		this.workingDirectory = workingDirectory;
	}
	
	@Override
	public File workingDir() {
		return this.workingDirectory;
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
		if (!initDirectories()) return false;
		this.registeredTasks.clear();
		
		buildFile = FileUtility.absolute(buildFile);
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

	/**
	 * Represents an build task and its dependencies that have to be executed
	 */
	public static record TaskNode(Optional<BuildTask> task, Set<TaskNode> dep) { }
	
	/**
	 * Checks if an task has to be executed and if so, prepares it to be executed.<br>
	 * Also performs the same operations for all registered dependencies of that task.
	 * @param taskName The name of the task to prepare
	 */
	protected void prepareTask(String taskName) {
		
		BuildTask task = taskNamed(taskName);
		if (task == null) {
			throw BuildScriptException.msg("task '%s' does not exist", taskName);
		}
		
		Set<String> dependencies = this.taskDependencies.get(taskName);
		
		Set<TaskNode> dependendNodes = new HashSet<>();
		if (dependencies != null) {
			for (String depTask : dependencies) {
				try {
					prepareTask(depTask);
				} catch (MetaScriptException e) {
					throw BuildScriptException.msg(e, "problem with task '%s' required by '%s'", depTask, taskName);
				}
				if (this.taskTree.task().isPresent()) dependendNodes.add(this.taskTree);
			}
		}
		
		try {
			if (!task.state().requiresBuild() && dependendNodes.isEmpty()) {
				this.taskTree = new TaskNode(Optional.empty(), new HashSet<>());
				return;
			}
		} catch (MetaScriptException e) {
			throw BuildScriptException.msg(e, "failed to query state for task: %s", task.name);
		}
		
		if (!this.task2node.containsKey(task)) this.task2node.put(task, new TaskNode(Optional.of(task), dependendNodes));
		this.taskTree = this.task2node.get(task);
		
	}
	
	/**
	 * Checks which tasks are required to complete the requested tasks and prepares them for execution.<br>
	 * Build a tree of tasks and their dependencies that can then be executed.
	 * @param tasks The list of tasks to execute.
	 * @return true if and only if all tasks where prepared for execution successfully.
	 */
	public boolean buildTaskTree(List<String> tasks) {
		try {

			this.task2node.clear();
			this.taskTree = null;
			
			Set<TaskNode> dependencies = new HashSet<>();
			
			for (String task : tasks) {
				try {
					prepareTask(task);
				} catch (MetaScriptException e) {
					logger().errort(LOG_TAG, "failed to build task tree for task: %s", task);
					e.printStack(logger().errorPrinter(LOG_TAG));
					return false;
				}
				dependencies.add(this.taskTree);
			}
			
			this.taskTree = new TaskNode(Optional.of(RootTask.TASK), dependencies);
			return true;
			
		} catch (MetaScriptException e) {
			logger().errort(LOG_TAG, "failed to prepare all tasks:");
			e.printStack(logger().errorPrinter(LOG_TAG));
			return false;
		} catch (Throwable e) {
			logger().errort(LOG_TAG, "uncatched exception while building task tree:", e);
			return false;
		}
	}
	
	/**
	 * Executes the prepared task tree, from the specified node upwards.
	 * @param node The node in the task tree to run
	 * @return true if and only if all the tasks from the node upward have completed successfully
	 */
	protected CompletableFuture<Void> runTaskTree(TaskNode node) {
		return CompletableFuture.runAsync(() -> {
			Map<TaskNode, CompletableFuture<Void>> tasks = node.dep().stream().collect(Collectors.toMap(tn -> tn, tn -> runTaskTree(tn)));
			try {
				CompletableFuture.allOf(tasks.values().toArray(CompletableFuture[]::new)).join();
			} catch (Exception ea) {
				for (var task : tasks.entrySet()) {
					try {
						task.getValue().join();
					} catch (CompletionException e) {
						if (e.getCause() instanceof MetaScriptException me) {
							String thisName = task.getKey().task().isPresent() ? task.getKey().task().get().name : "nothing";
							String parentName = node.task().isPresent() ? node.task().get().name : "nothing";
							if (!node.task().isEmpty()) node.task().get().failedDependency();
							throw BuildException.msg(me, "problem with task '%s' required by '%s'!", thisName, parentName);
						} else if (e != null) {
							throw new AssertionError("uncatched exception: ", e.getCause());
						}
					}
				}
			}
		}).thenAcceptAsync(v -> {
			if (node.task().isEmpty()) return;
			if (this.statusCallback != null) this.statusCallback.taskStarted(node.task().get().name);
			boolean result = node.task().get().runTask(
					this.statusCallback != null ? status -> statusCallback.taskStatus(node.task().get().name, status) : null);
			if (this.statusCallback != null) this.statusCallback.taskCompleted(node.task().get().name);
			if (!result) {
				throw BuildException.msg("task '%s' failed!", node.task().get().name);
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
		for (BuildTask task : this.registeredTasks.values()) {
			if (task.state() == TaskState.UPTODATE) upToDate++;
			if (task.state() == TaskState.FAILED) failed++;
		}
		
		logger().infot(LOG_TAG, "TASKS: %d  UP_TO_DATE: %s  FAILED: %d", this.registeredTasks.size() - 1, upToDate, failed);
		
	}
	
	@Override
	public boolean runTasks(List<String> tasks) {

		for (BuildTask task : this.registeredTasks.values()) task.reset();
		
		logger().infot(LOG_TAG, "begin build init phase");
		
		if (!buildTaskTree(tasks)) {
			logger().errort(LOG_TAG, "could not build task tree, abort build!");
			return false;
		}
		
		if (this.statusCallback != null) this.statusCallback.taskCount(this.task2node.size());
		
		logger().infot(LOG_TAG, "begin build run phase");
		
		if (this.taskTree.dep().stream().filter(n -> n.task().isPresent()).count() == 0) {
			logger().infot(LOG_TAG, "nothing to do");
			printStatus();
			return true;
		}
		
		this.taskQueue = new ArrayBlockingQueue<>(this.registeredTasks.size());
		this.taskExecutor = new ThreadPoolExecutor(0, this.taskThreads, 10, TimeUnit.SECONDS, this.taskQueue);
		
		boolean success = false;
		try {
			runTaskTree(this.taskTree).join();
			logger().infot(LOG_TAG, "build completed");
			success = true;
		} catch (CompletionException e)  {
			if (e.getCause() instanceof MetaScriptException me) {
				logger().errort(LOG_TAG, "build task error:");
				me.printStack(logger().errorPrinter(LOG_TAG));
			} else {
				logger().errort(LOG_TAG, "uncatched build task error:", e.getCause());
			}
		}
		
		logger().infot(LOG_TAG, "build run phase finished, shuting down");
		
		this.registeredTasks.values().forEach(BuildTask::cleanupTask);
		
		try {
			if (!this.taskExecutor.shutdownNow().isEmpty()) {
				logger().warnt(LOG_TAG, "tasks still runing after build, this indicates sirious problems with the build file!");
				logger().warnt(LOG_TAG, "attempt force termination of remaining tasks ...");
			}
			if (!this.taskExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
				logger().warnt(LOG_TAG, "failed to shutdown build tasks, executor not responding!");
			}
			this.taskQueue.clear();
		} catch (InterruptedException e) {}
		
		this.refreshDependencies = false;
		
		printStatus();
		
		return success;
		
	}
	
}
