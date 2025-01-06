package de.m_marvin.metabuild.core;

public interface IStatusCallback {
	
	public void taskCount(int taskCount);
	public void taskStatus(String task, String status);
	public void taskCompleted(String task);
	
}
