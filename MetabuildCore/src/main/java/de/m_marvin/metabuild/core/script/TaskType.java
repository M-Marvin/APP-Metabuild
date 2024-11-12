package de.m_marvin.metabuild.core.script;

import java.util.HashMap;
import java.util.Map;

public final class TaskType {
	
	private static final Map<String, TaskType> registered = new HashMap<>();
	
	private final String name;
	
	private TaskType(String name) {
		this.name = name;
	}
	
	public static TaskType named(String name) {
		if (!registered.containsKey(name)) {
			registered.put(name, new TaskType(name));
		}
		return registered.get(name);
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return this.name.toLowerCase();
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj == this;
	}
	
}
