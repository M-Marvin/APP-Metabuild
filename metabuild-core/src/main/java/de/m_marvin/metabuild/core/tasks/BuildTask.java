package de.m_marvin.metabuild.core.tasks;

import java.util.function.Consumer;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.simplelogging.api.Logger;

/**
 * An task that has to be run to produce results from its inputs.<br>
 * Every build file consists of multiple build tasks.
 */
public class BuildTask {
	
	public TaskType type = TaskType.named("undefined");
	public String name;
	public String group;
	public TaskState state;
	
	private BuildScript buildscript;
	private Consumer<String> statusCallback;
	
	/**
	 * Create and register a new task
	 * @param name The name of the task
	 */
	public BuildTask(String name) {
		this.name = name;
		if (this.getClass() != RootTask.class && !Metabuild.get().registerTask(this))
			throw BuildScriptException.msg("failed to construct new task '%s'", name);
	}
	
	public void setBuildscript(BuildScript buildscript) {
		this.buildscript = buildscript;
	}
	
	public BuildScript buildscript() {
		return this.buildscript;
	}
	
	public String fullName() {
		return this.buildscript.buildName + ":" + this.name;
	}
	
	/**
	 * Register new dependencies for this task
	 * @param taskName The name of the dependency tasks
	 */
	public void dependsOn(String... taskName) {
		Metabuild.get().taskDepend(this, Stream.of(taskName).map(Metabuild.get()::taskNamed).toArray(BuildTask[]::new));
	}
	
	/**
	 * Register new dependencies for this task
	 * @param task The dependency tasks
	 */
	public void dependsOn(BuildTask... task) {
		Metabuild.get().taskDepend(this, task);
	}
	
	/**
	 * Register this task as dependency for an another task
	 * @param task The task to add this task as dependency to
	 */
	public void dependencyOf(String task) {
		Metabuild.get().taskDepend(Metabuild.get().taskNamed(task), this);
	}

	/**
	 * Register this task as dependency for an another task
	 * @param task The task to add this task as dependency to
	 */
	public void dependencyOf(BuildTask task) {
		Metabuild.get().taskDepend(task, this);
	}
	
	public Logger logger() {
		return Metabuild.get().logger();
	}
	
	protected String logTag() {
		return String.format("%s/%s", this.type.toString(), this.name);
	}
	
	public void reset() {
		this.state = null;
	}
	
	/**
	 * Returns the current state of the task.<br>
	 * If the task was not yet prepared, a preparation is performed to query the task state.
	 * @return The current state of the task
	 */
	public TaskState state() {
		if (this.state == null) this.state = prepare();
		return this.state;
	}
	
	/**
	 * Called before actual call to the run() method, to determine if the task needs to run at all, and if so, prepare itself for the execution.
	 * @return The state of the task
	 */
	protected TaskState prepare() {
		return TaskState.UPTODATE;
	}
	
	/**
	 * Called from the build system if an dependency of this task has failed, to update its state
	 */
	public void failedDependency() {
		this.state = TaskState.INCOMPLETE;
	}
	
	/**
	 * Runs a preparation and if neccessary, then runs this task.
	 * @return true if and only if the task completed successfully
	 */
	public boolean runTask(Consumer<String> statusCallback) {
		this.statusCallback = statusCallback;
		if (!prepare().requiresBuild()) {
			this.state = TaskState.UPTODATE;
			return true;
		}
		if (this.statusCallback != null) statusCallback.accept("running");
		if (run()) {
			this.state = TaskState.UPTODATE;
			return true;
		} else {
			this.state = TaskState.FAILED;
			return false;
		}
	}
	
	/**
	 * Can be used to report information about the current progress of this task to the user.
	 * @param status Status message to display
	 */
	protected void status(String status) {
		if (this.statusCallback != null) this.statusCallback.accept(status);
	}
	
	/**
	 * Called when the task needs to be executed.<br>
	 * This method performs the actual work of the task.
	 * @return true if and only if the task completed successfully
	 */
	protected boolean run() {
		return true;
	}
	
	/**
	 * Run on each task after an build has completed, no matter if this task did run or not.
	 */
	protected void cleanup() {}
	
	public void cleanupTask() {
		try {
			cleanup();
		} catch (Exception e) {
			logger().errort(logTag() + "/cleanup", "task cleanup did throw an exception, this should not happen!");
		}
	}
	
	/**
	 * The different states a task can have before and after execution
	 */
	public static enum TaskState {
		/**
		 * The tasks results are outdated, and the task needs to be run again.
		 */
		OUTDATED(true),
		/**
		 * The tasks results are up to date, and the task does not need to be run again.
		 */
		UPTODATE(false),
		/**
		 * The task failed to run, any produced results are invalid.
		 */
		FAILED(true),
		/**
		 * The task could not run because an dependency task did not complete successfully.
		 */
		INCOMPLETE(true);
		
		private final boolean requiredBuild;
		
		private TaskState(boolean requiresBuild) {
			this.requiredBuild = requiresBuild;
		}
		
		public boolean requiresBuild() {
			return requiredBuild;
		}
	}
	
}
