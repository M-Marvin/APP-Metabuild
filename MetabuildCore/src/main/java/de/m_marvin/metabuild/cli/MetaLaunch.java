package de.m_marvin.metabuild.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.m_marvin.commandlineparser.CommandLineParser;
import de.m_marvin.metabuild.core.Metabuild;

public class MetaLaunch {
	
	public static void main(String... args) {
		
		// Read list of tasks to run
		List<String> taskRunList = new ArrayList<>();
		for (String s : args) {
			if (Metabuild.TASK_NAME_FILTER.matcher(s).matches()) taskRunList.add(s);
		}
		
		// Initialize argument parser
		CommandLineParser parser = new CommandLineParser();
		parser.addOption("help", false, "show command help");
		parser.addOption("build-dir", Metabuild.DEFAULT_BUILD_DIRECTORY, "directory to place build files in");
		parser.addOption("build-file", Metabuild.DEFAULT_BUILD_FILE_NAME, "build file to load and run tasks from");
		parser.addOption("cache-dir", Metabuild.DEFAULT_CACHE_DIRECTORY, "directory to save all cache data");
		parser.addOption("log", Metabuild.DEFAULT_BUILD_LOG_NAME, "file to write build log to");
		
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
	
	public static int launchMetabuild(File workingDir, List<String> taskList, CommandLineParser args) {
		
		Metabuild mb = new Metabuild(workingDir);
		
		// TODO initialize CLI-UI output
		if (args.getOption("build-dir") != null)
			mb.setBuildDirectory(new File(args.getOption("build-dir")));
		if (args.getOption("log") != null)
			mb.setLogFile(new File(args.getOption("log")));
		if (args.getOption("cache-dir") != null)
			mb.setCacheDirectory(new File(args.getOption("cache-dir")));
		
		// Load build file
		File buildFile = new File(Metabuild.DEFAULT_BUILD_FILE_NAME);
		if (args.getOption("build-file") != null)
			buildFile = new File(args.getOption("build-file"));
		if (!mb.initBuild(buildFile)) return -1;
		
		// Run tasks
		for (String taskName : taskList) {
			if (!mb.runTask(taskName)) return -2;
		}
		
		Metabuild.terminate();
		return 0;
		
	}
	
}
