package de.m_marvin.metabuild.core.tasks;

import de.m_marvin.metabuild.core.script.TaskType;

public class RootTask extends BuildTask {
	
	public static final RootTask TASK = new RootTask("root");
	
	private RootTask(String name) {
		super(name);
		this.type = TaskType.named("ROOT");
	}
	
	@Override
	public TaskState state() {
		return TaskState.INCOMPLETE;
	}
	
	@Override
	public boolean run() {
		return true;
	}
	
}
