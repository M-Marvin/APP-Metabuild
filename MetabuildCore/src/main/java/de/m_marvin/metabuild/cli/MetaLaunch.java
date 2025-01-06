package de.m_marvin.metabuild.cli;

import java.io.File;
import java.io.PrintStream;
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
	private static TerminalLogger terminal;
	
	public static void setupCLIUI(Metabuild mb) {
		
		terminal = new TerminalLogger();
		mb.setConsoleLogger(terminal);
		
		mb.setStatusCallback(new IStatusCallback() {
			
			@Override
			public void taskStatus(String task, String status) {
				taskStates.put(task, status);
				printStatusUI(terminal);
			}
			
			@Override
			public void taskCount(int taskCount) {
				tasksCount = taskCount;
				printStatusUI(terminal);
			}
			
			@Override
			public void taskCompleted(String task) {
				taskStates.remove(task);
				if (!task.equals("root")) tasksCompleted++;
				printStatusUI(terminal);
			}
			
		});
		
	}
	
	public static void printStatusUI(TerminalLogger logger) {
		
		PrintStream printer = logger.getNewPrinter();
		
		float progress = tasksCompleted / (float) tasksCount;
		
		int p = Math.round(progress * 50);
		printer.print("[");
		for (int i = 0; i < 50; i++) printer.print(i < p ? "=" : "-");
		printer.println("]");
		printer.println("Progress: " + progress * 100);
		
		printer.println("");
		
		for (var e : taskStates.entrySet()) {
			printer.println(e.getKey() + "\t" + e.getValue());
		}
		
		logger.printUI(false);
		
	}
	
	public static int launchMetabuild(File workingDir, List<String> taskList, CommandLineParser args) {
		
		Metabuild mb = new Metabuild(workingDir);
		
		setupCLIUI(mb);
		
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
		if (!mb.runTasks(taskList)) {
			Metabuild.terminate();
			return -1;
		}
		
		Metabuild.terminate();
		terminal.printUI(true);
		return 0;
		
	}
	
}
