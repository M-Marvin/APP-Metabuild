package de.m_marvin.metabuild.core.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.m_marvin.cliutil.arguments.Arguments;
import de.m_marvin.cliutil.arguments.CommandArgumentParser;
import de.m_marvin.cliutil.exception.CommandArgumentException;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.core.Metabuild;

public class MetaLaunch {
	
	private MetaLaunch() {}
	
	public static void main(String... args) {
		
		// Read list of tasks to run
		List<String> taskRunList = new ArrayList<>();
		for (String s : args) {
			if (!Metabuild.TASK_NAME_FILTER.matcher(s).matches()) break;
			taskRunList.add(s);
		}
		
		// Initialize argument parser
		CommandArgumentParser parser = new CommandArgumentParser();
		parser.addOption("help", false, "show command help");
		parser.addOption("build-file", Metabuild.DEFAULT_BUILD_FILE_NAME, "build file to load and run tasks from");
		parser.addOption("cache-dir", Metabuild.DEFAULT_CACHE_DIRECTORY, "directory to save all cache data");
		parser.addOption("log", Metabuild.DEFAULT_BUILD_LOG_NAME, "file to write build log to");
		parser.addOption("threads", Integer.toString(Metabuild.DEFAULT_TASK_THREADS), "number of threads to utilize for executing build tasks");
		parser.addOption("refresh-dependencies", false, "if set, re-download all dependencies and replace current cache");
		parser.addOption("info", false, "Print additional log information to the terminal during build process");
		parser.addOption("force", false, "if set, all tasks are run even if they are up to date");
		parser.addOption("prepare", false, "Skip actual run phase and only run prepare phase");
		
		try {
			
			// Parse and check arguments
			Arguments arguments = parser.parse(Arrays.copyOfRange(args, taskRunList.size(), args.length));
			if (args.length == 0 || arguments.flag("help")) {
				System.out.println("meta < [tasks] ... > < options >");
				System.out.println(parser.printHelp());
				System.exit(1);
			}

			// Launch and run metabuild
			try {
				File workingDir = new File(System.getProperty("user.dir"));
				int r = launchMetabuild(workingDir, taskRunList, arguments);
				System.exit(r);
			} catch (Throwable e) {
				e.printStackTrace();
				System.exit(2);
			}
			
		} catch (CommandArgumentException e) {
			System.err.println(e.getMessage());
		}
		
	}
	
	public static int launchMetabuild(File workingDir, List<String> taskList, Arguments args) throws CommandArgumentException {
		
		IMeta mb;
		try {
			mb = IMeta.instantiateMeta(MetaLaunch.class.getClassLoader());
		} catch (InstantiationException e) {
			e.printStackTrace();
			return -1;
		}
		
		mb.setWorkingDirectory(workingDir);
		
		if (args.get("log") != null)
			mb.setLogFile(args.get("log"));
		if (args.get("cache-dir") != null)
			mb.setCacheDirectory(args.get("cache-dir"));
		if (args.flag("refresh-dependencies"))
			mb.setRefreshDependencies(true);
		if (args.flag("force"))
			mb.setForceRunTasks(true);
		if (args.flag("prepare"))
			mb.setSkipTaskRun(true);
		boolean printLogs = args.get("info");
		
		mb.setTerminalOutput(System.out, !printLogs);
		
		mb.setConsoleStreamInput(System.in);
		
		// Load build file
		File buildFile = Metabuild.DEFAULT_BUILD_FILE_NAME;
		if (args.get("build-file") != null)
			buildFile = args.get("build-file");
		if (!mb.initBuild(buildFile)) return -1;
		
		// Parse build threads
		if (args.get("threads") != null)
			mb.setTaskThreads(Integer.parseInt(args.get("threads")));
		
		// Run tasks
		boolean buildState = mb.runTasks(taskList);
		
		mb.terminate();
		return buildState ? 0 : -1;
		
	}
	
}
