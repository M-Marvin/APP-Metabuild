package de.m_marvin.metabuild.core.script;

import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.simplelogging.api.Logger;

public abstract class BuildTask {
	
	public TaskType type;
	public String name;
	
	public BuildTask(String name) {
		this.name = name;
		if (!Metabuild.get().registerTask(this))
			throw BuildScriptException.msg("failed to construct new task '%s'", name);
	}
	
	public void dependsOn(String... taskName) {
		Metabuild.get().taskDepend(this, Stream.of(taskName).map(Metabuild.get()::taskNamed).toArray(BuildTask[]::new));
	}
	
	public void dependsOn(BuildTask... task) {
		Metabuild.get().taskDepend(this, task);
	}
	
	public Logger logger() {
		return Metabuild.get().logger();
	}
	
	protected String logTag() {
		return String.format("%s/%s", this.type.toString(), this.name);
	}
	
	public TaskState prepare() {
		return TaskState.OUTDATED;
	}
	
	public abstract boolean run();
	
	public static enum TaskState {
		OUTDATED(true),
		UPTODATE(false),
		FAILED(true);
		
		private final boolean requiredBuild;
		
		private TaskState(boolean requiresBuild) {
			this.requiredBuild = requiresBuild;
		}
		
		public boolean requiresBuild() {
			return requiredBuild;
		}
	}
	
}
