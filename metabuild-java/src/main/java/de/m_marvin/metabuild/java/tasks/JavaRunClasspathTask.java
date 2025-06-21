package de.m_marvin.metabuild.java.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.MetaScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.core.util.ProcessUtility;

public class JavaRunClasspathTask extends BuildTask {

	public final List<File> classpath = new ArrayList<>(); 
	public final List<File> classesDir = new ArrayList<File>();
	public String javaExecutable = "java";
	public String mainClass = null;
	public final List<String> arguments = new ArrayList<>();
	public Predicate<Integer> exitCondition = i -> i == 0;
	
	public JavaRunClasspathTask(String name) {
		super(name);
		this.type = TaskType.named("java_run");
	}
	
	@Override
	protected TaskState prepare() {
		return TaskState.OUTDATED;
	}
	
	@Override
	protected boolean run() {
		
		Collection<File> classesDir = FileUtility.parseFilePaths(this.classesDir);
		
		if (this.mainClass == null) {
			logger().errort(logTag(), "main class not set!");
			return false;
		}
		
		StringBuffer classpathBuf = new StringBuffer();
		classesDir.forEach(f -> classpathBuf.append(f).append(";"));
		
		String classpathStr = FileUtility.parseFilePaths(this.classpath).stream().map(File::getAbsolutePath).reduce((a, b) -> a + ";" + b).orElse("");
		classpathBuf.append(classpathStr);
		
		Optional<File> javaExecutable = FileUtility.locateOnPath(this.javaExecutable);
		if (javaExecutable.isEmpty()) {
			logger().errort(logTag(), "failed to locate JVM executable: %s", this.javaExecutable);
			return false;
		}
		
		List<String> commandLine = new ArrayList<>();
		commandLine.add(javaExecutable.get().getAbsolutePath());
		commandLine.add("-classpath");
		commandLine.add(classpathBuf.toString());
		commandLine.add(this.mainClass);
		commandLine.addAll(this.arguments.stream().filter(a -> a != null).toList());
		
		ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
		
		try {
			// Start process
			logger().warnt(logTag(), "starting process: %s", this.mainClass);
			logger().debugt(logTag(), "java cmd: %s", commandLine.stream().reduce((a, b) -> a + " " + b).get());
			int exitCode = ProcessUtility.runProcess(logger(), processBuilder, this::shouldAbort);
			logger().warnt(logTag(), "process terminated, exit code: %d", exitCode);
			return this.exitCondition.test(exitCode);
		} catch (MetaScriptException e) {
			throw BuildException.msg(e, "failed to run java process: %s", javaExecutable.get());
		}
		
	}
	
}
