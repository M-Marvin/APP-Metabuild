package de.m_marvin.metabuild.core.cli;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.IMeta.IStatusCallback;

public class OutputHandler {
	
	private IMeta meta;
	private PrintStream print;
	private boolean printUI;
	private int tasksCount = 0;
	private int tasksCompleted = 0;
	private Map<String, String> taskStates = new HashMap<>();
	
	public OutputHandler(IMeta mb, PrintStream stream, boolean printUI) {
		
		this.meta = mb;
		this.print = new PrintStream(stream);
		this.printUI = printUI;
		
		if (printUI) {
			mb.setLogStreamOutput(null);
		} else {
			mb.setLogStreamOutput(print);
		}

		meta.addStatusCallback(new IStatusCallback() {
			
			@Override
			public void taskStatus(String task, String status) {
				if (OutputHandler.this.printUI) cleanStatusUI();
				taskStates.put(task, status);
				if (OutputHandler.this.printUI) printStatusUI();

			}
			
			@Override
			public void taskCount(int taskCount) {
				tasksCount = taskCount;
				if (OutputHandler.this.printUI) printStatusUI();
			}
			
			@Override
			public void taskStarted(String task) {
				if (!task.equals("root")) {
					tasksCompleted++;
					if (OutputHandler.this.printUI) {
						cleanStatusUI();
						printStatusUI();
					} else {
						printTaskSeperator(task, tasksCompleted, tasksCount);
					}
				}
			}
			
			@Override
			public void taskCompleted(String task) {
				if (OutputHandler.this.printUI) cleanStatusUI();
				taskStates.remove(task);
				if (OutputHandler.this.printUI) printStatusUI();
			}
			
			@Override
			public void buildCompleted(boolean success) {
				printFinish(success);
			}
			
		});
		
	}
	
	protected void printFinish(boolean successfull) {
		if (tasksCount == 0) {
			print.print("\n \033[38;5;255mStatus: <\033[38;5;46mNOTHING TO DO\033[38;5;255m>\033[0m\n");
		} else if (successfull) {
			print.print("\n \033[38;5;255mStatus: <\033[38;5;46mSUCCESSFULL\033[38;5;255m>\033[0m\n");
		} else {
			print.print("\n \033[38;5;255mStatus: <\033[38;5;196mFAILED\033[38;5;255m>\033[0m\n");
		}
	}
	
	protected void printTaskSeperator(String task, int nr, int total) {
		print.println(String.format("\n \033[38;5;255m[\033[38;5;248m%02d/%02d\033[38;5;255m] > %s\033[0m\n", nr, total, task));
	}
	
	protected void cleanStatusUI() {
		print.print(String.format("\033[%dA\033[0J", 2 + taskStates.size()));
	}
	
	protected void printStatusUI() {
		
		if (tasksCount == 0) return;
		float progress = tasksCompleted / (float) tasksCount;
		
		int p = Math.round(progress * 50);
		print.print("\n\033[38;5;190m[");
		for (int i = 0; i < 50; i++) print.print(i < p ? "\033[38;5;46m=" : "\033[38;5;8m-");
		print.print(String.format("\033[38;5;190m]\033[38;5;231m %d%%\n", Math.round(progress * 100)));
		
		for (var e : taskStates.entrySet()) {
			print.println(String.format("\033[38;5;231m%s > %s\033[0m", e.getKey(), e.getValue()));
		}
		
	}
	
}
