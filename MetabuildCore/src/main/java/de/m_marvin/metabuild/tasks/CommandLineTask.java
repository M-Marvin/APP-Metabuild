package de.m_marvin.metabuild.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;

public class CommandLineTask extends BuildTask {

	public File executable;
	public List<String> arguments = new ArrayList<>();
	public Predicate<Integer> exitCondition = i -> i == 0;
	
	public CommandLineTask(String name) {
		super(name);
	}
	
	public String[] buildCommand() {
		String cmdPath = FileUtility.absolute(this.executable).getAbsolutePath();
		List<String> params = new ArrayList<>();
		params.add(cmdPath);
		params.addAll(this.arguments);
		return params.toArray(String[]::new);
	}
	
	@Override
	public boolean run() {
		
		String[] command = buildCommand();
		
		if (command.length == 0) return true;
		
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(Metabuild.get().workingDir());
		
		logger().debugt(logTag(), "cmd: %s", Stream.of(command).reduce((a, b) -> String.format("%s %s", a, b)).get());
		
		try {
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			return this.exitCondition.test(exitCode);
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to run command: ", command[0]);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
	}
	
}
