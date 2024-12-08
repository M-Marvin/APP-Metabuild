package de.m_marvin.metabuild.tasks;

import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.simplelogging.api.Logger;

/**
 * An task that has to be run to produce results from its inputs.<br>
 * Every build file consists of multiple build tasks.
 */
public abstract class BuildTask {
	
	public TaskType type;
	public String name;
	public TaskState state;
	
	/**
	 * Create and register a new task
	 * @param name The name of the task
	 */
	public BuildTask(String name) {
		this.name = name;
		if (!Metabuild.get().registerTask(this))
			throw BuildScriptException.msg("failed to construct new task '%s'", name);
	}
	
	/**
	 * Register a new dependency for this task
	 * @param taskName The name of the dependency task
	 */
	public void dependsOn(String... taskName) {
		Metabuild.get().taskDepend(this, Stream.of(taskName).map(Metabuild.get()::taskNamed).toArray(BuildTask[]::new));
	}
	
	/**
	 * Register a new dependency for this task
	 * @param task The dependency task
	 */
	public void dependsOn(BuildTask... task) {
		Metabuild.get().taskDepend(this, task);
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
		return TaskState.OUTDATED;
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
	public boolean runTask() {
		if (!prepare().requiresBuild()) {
			this.state = TaskState.UPTODATE;
			return true;
		}
		if (run()) {
			this.state = TaskState.UPTODATE;
			return true;
		} else {
			this.state = TaskState.FAILED;
			return false;
		}
	}
	
	/**
	 * Called when the task needs to be executed.<br>
	 * This method performs the actual work of the task.
	 * @return true if and only if the task completed successfully
	 */
	protected abstract boolean run();
	
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