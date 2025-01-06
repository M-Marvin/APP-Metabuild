package de.m_marvin.metabuild.tasks.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.tasks.BuildTask;

public class JavaRunClasspathTask extends BuildTask {

	public File classpath = new File(".classpath"); 
	public File classesDir = new File("classes/default");
	public File workingDir = null;
	public String mainClass = null;
	public final List<String> arguments = new ArrayList<>();
	
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
		
		File classpathFile = this.classpath != null ? FileUtility.absolute(this.classpath) : null;
		File classesDir = FileUtility.absolute(this.classesDir);
		File workingDir = this.workingDir != null ? FileUtility.absolute(this.workingDir) : classesDir;
		
		if (this.mainClass == null) {
			logger().errort(logTag(), "main class not set!");
			return false;
		}
		
		if (!classesDir.isDirectory()) {
			logger().errort(logTag(), "classes direcoty not found: %s", this.classesDir);
			return false;
		}
		
		if (!workingDir.isDirectory() && !workingDir.mkdirs()) {
			logger().errort(logTag(), "failed to create run workink dir: %s", this.workingDir != null ? this.workingDir : this.classesDir);
			return false;
		}
		
		StringBuffer classpathBuf = new StringBuffer();
		classpathBuf.append(classesDir).append(";");
		
		if (classpathFile != null) {
			try {
				InputStream is = new FileInputStream(classpathFile);
				classpathBuf.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
				is.close();
			} catch (IOException e) {
				throw BuildException.msg(e, "failed to acces classpath file: %s", this.classpath);
			}
		}
		
		Optional<String> javaExecutable = ProcessHandle.current().info().command();
		if (javaExecutable.isEmpty()) {
			logger().errort(logTag(), "failed to get JVM executable!");
			return false;
		}
		
		List<String> commandLine = new ArrayList<>();
		commandLine.add(javaExecutable.get());
		commandLine.add("-classpath");
		commandLine.add(classpathBuf.toString());
		commandLine.add(this.mainClass);
		commandLine.addAll(this.arguments.stream().filter(a -> a != null).toList());
		
		ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
		processBuilder.inheritIO();
		try {
			logger().warnt(logTag(), "starting process: %s", this.mainClass);
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			logger().warnt(logTag(), "process terminated, exit code: %d", exitCode);
			return true;
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to initiate java process: %s", javaExecutable.get());
		} catch (InterruptedException e) {
			throw BuildException.msg(e, "interrupted while waring for java process: %s", javaExecutable.get());
		}
		
	}
	
}
