package de.m_marvin.metabuild.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.m_marvin.commandlineparser.CommandLineParser;
import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.Metabuild.IStatusCallback;
import de.m_marvin.metabuild.maven.MavenResolver;

public class MetaLaunch {
	
	public static void main(String... args) {
		
		// Read list of tasks to run
		List<String> taskRunList = new ArrayList<>();
		for (String s : args) {
			if (!Metabuild.TASK_NAME_FILTER.matcher(s).matches()) break;
			taskRunList.add(s);
		}
		
		// Initialize argument parser
		CommandLineParser parser = new CommandLineParser();
		parser.addOption("help", false, "show command help");
		parser.addOption("build-file", Metabuild.DEFAULT_BUILD_FILE_NAME, "build file to load and run tasks from");
		parser.addOption("cache-dir", Metabuild.DEFAULT_CACHE_DIRECTORY, "directory to save all cache data");
		parser.addOption("log", Metabuild.DEFAULT_BUILD_LOG_NAME, "file to write build log to");
		parser.addOption("threads", Integer.toString(Metabuild.DEFAULT_TASK_THREADS), "number of threads to utilize for executing build tasks");
		parser.addOption("strict-maven-meta", MavenResolver.strictMavenMeta, "if set, only download artifacts registered in repo maven-meta");
		parser.addOption("strict-maven-hash", MavenResolver.strictHashVerify, "if set, only download artifacts with valid (or no existing) hash signatures");
		parser.addOption("refresh-dependencies", false, "if set, re-download all dependencies and replace current cache");
		
		// Parse and check arguments
		parser.parseInput(Arrays.copyOfRange(args, taskRunList.size(), args.length));
		if (args.length == 0 || parser.getFlag("help")) {
			System.out.println("metabuild < [tasks] ... > < options >");
			System.out.println(parser.printHelp());
			System.exit(1);
		}

		// Launch and run metabuild
		try {
			File workingDir = new File(System.getProperty("user.dir"));
			int r = launchMetabuild(workingDir, taskRunList, parser);
			System.exit(r);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(2);
		}
		
	}
	
	private static int tasksCount = 0;
	private static int tasksCompleted = 0;
	private static Map<String, String> taskStates = new HashMap<>();
	
	public static void setupCLIUI(Metabuild mb, boolean printUI) {
		
		mb.setTerminalOutput(false);
		
		mb.setStatusCallback(new IStatusCallback() {
			
			@Override
			public void taskStatus(String task, String status) {
				if (printUI) cleanStatusUI();
				taskStates.put(task, status);
				if (printUI) printStatusUI();
			}
			
			@Override
			public void taskCount(int taskCount) {
				tasksCount = taskCount;
				if (printUI) printStatusUI();
			}
			
			@Override
			public void taskStarted(String task) {
				if (!task.equals("root")) {
					tasksCompleted++;
					if (printUI) {
						cleanStatusUI();
						printStatusUI();
					} else {
						printTaskSeperator(task, tasksCompleted, tasksCount);
					}
				}
			}
			
			@Override
			public void taskCompleted(String task) {
				if (printUI) cleanStatusUI();
				taskStates.remove(task);
				if (printUI) printStatusUI();
			}
			
		});
		
	}

	public static void printFinish(boolean successfull) {
		if (successfull) {
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
		
		float progress = tasksCompleted / (float) tasksCount;
		
		int p = Math.round(progress * 50);
		System.out.print("\033[38;5;190m[");
		for (int i = 0; i < 50; i++) System.out.print(i < p ? "\033[38;5;46m=" : "\033[38;5;8m-");
		System.out.println(String.format("\033[38;5;190m]\033[38;5;231m %d%%\n", Math.round(progress * 100)));
		
		for (var e : taskStates.entrySet()) {
			System.out.println(String.format("\033[38;5;231m%s > %s\033[0m", e.getKey(), e.getValue()));
		}
		
	}
	
	public static int launchMetabuild(File workingDir, List<String> taskList, CommandLineParser args) {
		
		Metabuild mb = new Metabuild(workingDir);
		
		setupCLIUI(mb, false); // FIXEME CLIUI not working with log output (run task)
		
		if (args.getOption("log") != null)
			mb.setLogFile(new File(args.getOption("log")));
		if (args.getOption("cache-dir") != null)
			mb.setCacheDirectory(new File(args.getOption("cache-dir")));
		if (args.getFlag("strict-maven-meta"))
			MavenResolver.strictMavenMeta = true;
		if (args.getFlag("strict-maven-hash"))
			MavenResolver.strictHashVerify = true;
		if (args.getFlag("refresh-dependencies"))
			mb.setRefreshDependencies(true);
		
		// Load build file
		File buildFile = new File(Metabuild.DEFAULT_BUILD_FILE_NAME);
		if (args.getOption("build-file") != null)
			buildFile = new File(args.getOption("build-file"));
		if (!mb.initBuild(buildFile)) return -1;
		
		// Parse build threads
		if (args.getOption("threads") != null)
			mb.setTaskThreads(Integer.parseInt(args.getOption("threads")));
		
		// Run tasks
		boolean buildState = mb.runTasks(taskList);
		
		printFinish(buildState);

		Metabuild.terminate();
		return buildState ? 0 : -1;
		
	}
	
}
