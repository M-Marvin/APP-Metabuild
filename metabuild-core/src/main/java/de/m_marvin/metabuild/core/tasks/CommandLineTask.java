package de.m_marvin.metabuild.core.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.core.util.ProcessUtility;

public class CommandLineTask extends BuildTask {

	public File executable;
	public List<String> arguments = new ArrayList<>();
	public Predicate<Integer> exitCondition = i -> i == 0;
	
	public CommandLineTask(String name) {
		super(name);
	}
	
	public String[] buildCommand() {
		
		if (this.executable == null)
			throw BuildException.msg("executable path not set!");
		
		File file = FileUtility.absolute(this.executable);
		if (!file.isFile()) {
			Optional<File> pathFile = FileUtility.locateOnPath(this.executable.toString());
			if (pathFile.isPresent()) file = pathFile.get();
		}
		String cmdPath = file.getAbsolutePath();
		List<String> params = new ArrayList<>();
		params.add(cmdPath);
		params.addAll(this.arguments);
		return params.toArray(String[]::new);
	}
	
	@Override
	protected TaskState prepare() {
		return TaskState.OUTDATED;
	}
	
	@Override
	public boolean run() {
		
		String[] command = buildCommand();
		
		if (command.length == 0) return true;
		
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(Metabuild.get().buildWorkingDir());
		
		try {
			// Start process
			logger().debugt(logTag(), "cmd: %s", Stream.of(command).reduce((a, b) -> String.format("%s %s", a, b)).get());
			int exitCode = ProcessUtility.runProcess(logger(), processBuilder);
			
			return this.exitCondition.test(exitCode);
		} catch (MetaScriptException e) {
			throw BuildException.msg(e, "failed to run command: ", command[0]);
		}
		
	}
	
}
