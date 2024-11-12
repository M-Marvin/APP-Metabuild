package de.m_marvin.metabuild.core.script;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.simplelogging.api.Logger;

public abstract class BuildTask {
	
	public TaskType type;
	public String name;
	
	public BuildTask(String name) {
		this.name = name;
		if (!Metabuild.get().registerTask(name, this))
			throw BuildScriptException.msg("failed to construct new task '%s'", name);
	}
	
	public Logger logger() {
		return Metabuild.get().logger();
	}
	
	protected String logTag() {
		return String.format("%s/%s", this.type.toString(), this.name);
	}
	
	public abstract boolean run();
	
}
