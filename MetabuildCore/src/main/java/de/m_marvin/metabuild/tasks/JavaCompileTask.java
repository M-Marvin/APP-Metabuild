package de.m_marvin.metabuild.tasks;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.m_marvin.metabuild.core.script.BuildTask;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class JavaCompileTask extends BuildTask {
	
	public File sourcesDir = new File("src/java");
	public File classesDir = new File("classes/default");
	
	public JavaCompileTask(String name) {
		super(name);
		this.type = TaskType.named("JAVA_COMPILE");
	}
	
	@Override
	public boolean run() {
		
		File srcPath = FileUtility.absolute(this.sourcesDir);
		File binPath = FileUtility.absolute(this.classesDir);
		
		List<File> sourceFiles = FileUtility.deepList(
				srcPath, 
				f -> f.getName().substring(f.getName().lastIndexOf(".")).equalsIgnoreCase("class"));
		
		Map<File, File> compileMap = sourceFiles.stream()
				.collect(Collectors.toMap(k -> k, f -> FileUtility.concat(binPath, FileUtility.relative(f, srcPath))));
		
		logger().infot(logTag(), "compile java source files: %s", this.sourcesDir);
		
		// TODO
		for (Entry<File, File> e : compileMap.entrySet()) {
			System.out.println(e.getKey().getName() + " -> " + e.getValue().getName());
		}
		
		return true;
	}
	
}
