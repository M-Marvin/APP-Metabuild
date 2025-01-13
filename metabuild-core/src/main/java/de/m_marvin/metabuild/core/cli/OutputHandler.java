package de.m_marvin.metabuild.core.cli;

import java.util.HashMap;
import java.util.Map;

import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.IMeta.IStatusCallback;

// FIXME cli ui with log output
public class OutputHandler {

	private static int tasksCount = 0;
	private static int tasksCompleted = 0;
	private static Map<String, String> taskStates = new HashMap<>();
	
	public static void setupCLIUI(IMeta mb, boolean printUI) {
		
		if (printUI) mb.setTerminalOutput(null);
		
		if (printUI) {

			mb.setStatusCallback(new IStatusCallback() {
				
				@Override
				public void taskStatus(String task, String status) {
					cleanStatusUI();
					taskStates.put(task, status);
					printStatusUI();
				}
				
				@Override
				public void taskCount(int taskCount) {
					tasksCount = taskCount;
					printStatusUI();
				}
				
				@Override
				public void taskStarted(String task) {
					if (!task.equals("root")) {
						tasksCompleted++;
						cleanStatusUI();
						printStatusUI();
					}
				}
				
				@Override
				public void taskCompleted(String task) {
					cleanStatusUI();
					taskStates.remove(task);
					printStatusUI();
				}
				
			});
			
		} else {

			mb.setStatusCallback(new IStatusCallback() {
				
				@Override
				public void taskStatus(String task, String status) {
					taskStates.put(task, status);
				}
				
				@Override
				public void taskCount(int taskCount) {
					tasksCount = taskCount;
				}
				
				@Override
				public void taskStarted(String task) {
					if (!task.equals("root")) {
						tasksCompleted++;
						printTaskSeperator(task, tasksCompleted, tasksCount);
					}
				}
				
				@Override
				public void taskCompleted(String task) {
					taskStates.remove(task);
				}
				
			});
			
		}
		
	}

	public static void printFinish(boolean successfull) {
		if (tasksCount == 0) {
			System.out.print("\n \033[38;5;255mStatus: <\033[38;5;46mNOTHING TO DO\033[38;5;255m>\033[0m\n");
		} else if (successfull) {
			System.out.print("\n \033[38;5;255mStatus: <\033[38;5;46mSUCCESSFULL\033[38;5;255m>\033[0m\n");
		} else {
			System.out.print("\n \033[38;5;255mStatus: <\033[38;5;196mFAILED\033[38;5;255m>\033[0m\n");
		}
	}
	
	public static void printTaskSeperator(String task, int nr, int total) {
		System.out.println(String.format("\n \033[38;5;255m[%02d/%02d] > %s\033[0m\n", nr, total, task));
	}
	
	public static void cleanStatusUI() {
		System.out.print(String.format("\033[%dA\033[0J", 2 + taskStates.size()));
	}
	
	public static void printStatusUI() {
		
		if (tasksCount == 0) return;
		float progress = tasksCompleted / (float) tasksCount;
		
		int p = Math.round(progress * 50);
		System.out.print("\n\033[38;5;190m[");
		for (int i = 0; i < 50; i++) System.out.print(i < p ? "\033[38;5;46m=" : "\033[38;5;8m-");
		System.out.print(String.format("\033[38;5;190m]\033[38;5;231m %d%%\n", Math.round(progress * 100)));
		
		for (var e : taskStates.entrySet()) {
			System.out.println(String.format("\033[38;5;231m%s > %s\033[0m", e.getKey(), e.getValue()));
		}
		
	}
	
}
