package de.m_marvin.metabuild.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaInitError;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.script.BuildTask;
import de.m_marvin.metabuild.core.script.compile.ScriptCompiler;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.tasks.RootTask;
import de.m_marvin.simplelogging.Log;
import de.m_marvin.simplelogging.api.Logger;
import de.m_marvin.simplelogging.impl.MultiLogger;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.StreamLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;

public final class Metabuild {

	public static final String LOG_TAG = "Metabuild";
	
	public static final String DEFAULT_BUILD_FILE_NAME = "build.meta";
	public static final String DEFAULT_BUILD_LOG_NAME = "build.log";
	public static final String DEFAULT_CACHE_DIRECTORY = System.getProperty("user.home") + "/.meta";
	public static final int DEFAULT_TASK_THREADS = 8;
	
	public static final String BUILD_SCRIPT_CLASS_NAME = "Buildfile";
	public static final Pattern TASK_NAME_FILTER = Pattern.compile("[\\d\\w]+");
	
	private static Metabuild instance;
	
	private final File workingDirectory;
	private File cacheDirectory;
	private File logFile;
	private Logger logger;
	private int taskThreads;
	private OutputStream logStream;
	private IStatusCallback statusCallback;
	private BuildScript buildscript;
	private Map<BuildTask, TaskNode> task2node = new HashMap<>();
	private TaskNode taskTree;
	private BlockingQueue<Runnable> taskQueue;
	private ThreadPoolExecutor taskExecutor;
	
	private final ScriptCompiler buildCompiler;
	private final Map<String, BuildTask> registeredTasks = new HashMap<>();
	private final Map<String, Set<String>> taskDependencies = new HashMap<>();
	
	public Metabuild(File workingDirectory) {
		if (instance != null) throw MetaInitError.msg("can't instantiate multiple metabuild instances in same VM!");
		instance = this;
		
		this.workingDirectory = workingDirectory;
		setCacheDirectory(new File(DEFAULT_CACHE_DIRECTORY));
		setTaskThreads(DEFAULT_TASK_THREADS);
		
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

	public void setCacheDirectory(File cacheDirectory) {
		this.cacheDirectory = FileUtility.absolute(cacheDirectory);
	}

	public void setLogFile(File logFile) {
		this.logFile = FileUtility.absolute(logFile);
	}
	
	public void setTaskThreads(int taskThreads) {
		this.taskThreads = taskThreads;
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
	
	public BuildTask taskNamed(String name) {
		if (!this.registeredTasks.containsKey(name)) {
			logger.warnt(LOG_TAG, "No task named '%s' is registered!", name);
			return null;
		}
		return this.registeredTasks.get(name);
	}
	
	public void taskDepend(BuildTask task, BuildTask... dependencies) {
		Set<String> dep = this.taskDependencies.get(task.name);
		if (dep == null) this.taskDependencies.put(task.name, dep = new HashSet<>());
		dep.addAll(Stream.of(dependencies).map(t -> t.name).toList());
	}
	
	public boolean initDirectories() {
		if (!this.cacheDirectory.isDirectory() && !this.cacheDirectory.mkdir()) {
			logger().errort(LOG_TAG, "could not create cache directory: %s", this.cacheDirectory.getPath());
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

	public static record TaskNode(Optional<BuildTask> task, Set<TaskNode> dep) { }
	
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
			if (!task.prepare().requiresBuild() && dependendNodes.isEmpty()) {
				this.taskTree = new TaskNode(Optional.empty(), new HashSet<>());
				return;
			}
		} catch (MetaScriptException e) {
			throw BuildScriptException.msg(e, "failed to query state for task: %s", task.name);
		}
		
		if (!this.task2node.containsKey(task)) this.task2node.put(task, new TaskNode(Optional.of(task), dependendNodes));
		this.taskTree = this.task2node.get(task);
		
	}
	
	public boolean buildTaskTree(List<String> tasks) {
		try {

			this.task2node.clear();
			this.taskTree = null;
			
			for (BuildTask task : this.registeredTasks.values()) {
				if (task.prepare().requiresBuild() && tasks.contains(task.name))
					tasks.add(task.name);
			}
			
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
	
	protected CompletableFuture<Void> runTaskTree(TaskNode node) {
		return CompletableFuture.runAsync(() -> {
			for (TaskNode n : node.dep()) {
				try {
					runTaskTree(n).join();
				} catch (CompletionException e) {
					if (e.getCause() instanceof MetaScriptException me) {
						String thisName = n.task().isPresent() ? n.task().get().name : "nothing";
						String parentName = node.task().isPresent() ? node.task().get().name : "nothing";
						throw BuildException.msg(me, "problem with task '%s' required by '%s'!", thisName, parentName);
					} else if (e != null) {
						throw new AssertionError("uncatched exception: ", e.getCause());
					}
				}
			}
		}).thenAcceptAsync(v -> {
			if (node.task().isEmpty()) return;
			if (!node.task().get().run()) {
				throw BuildException.msg("task '%s' failed!", node.task().get().name);
			}
		}, this.taskExecutor);
	}

	public boolean runTasks(String... tasks) {
		return runTasks(Arrays.asList(tasks));
	}
	
	public boolean runTasks(List<String> tasks) {

		logger().infot(LOG_TAG, "begin build init phase");
		
		if (!buildTaskTree(tasks)) {
			logger().errort(LOG_TAG, "could not build task tree, abort build!");
			return false;
		}

		logger().infot(LOG_TAG, "begin build run phase");
		
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
		
		return success;
		
	}
	
}
