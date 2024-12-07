package de.m_marvin.metabuild.tasks;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class JavaCompileTask extends CommandLineTask {
	
	public File sourcesDir = new File("src/java");
	public File classesDir = new File("classes/default");
	public File jdkHome = new File(System.getenv("JAVA_HOME"));
	public Predicate<File> sourceFileFilter = f -> FileUtility.getExtension(f).equalsIgnoreCase("java");
	
	protected List<File> sourceFiles;
	
	public JavaCompileTask(String name) {
		super(name);
		this.type = TaskType.named("JAVA_COMPILE");
	}
	
	protected boolean findJavaCompiler() {
		
		File binDir = new File(this.jdkHome, "bin");
		
		Optional<File> compiler = Stream.of(binDir.listFiles())
			.filter(File::isFile)
			.filter(f -> FileUtility.getNameNoExtension(f).equals("javac"))
			.findFirst();
		
		if (compiler.isEmpty()) return false;
		
		this.executable = compiler.get();
		return true;
		
	}
	
	protected final static Pattern TIMESTAMP_ENTRY_PATTERN = Pattern.compile("^(\\d+):\"(.+)\"");
	
	protected String makePathArg(File path) {
		return String.format("\"%s\"", path.getAbsolutePath());
	}
	
	protected boolean filterSourceFiles() {

		File srcPath = FileUtility.absolute(this.sourcesDir);
		File classPath = FileUtility.absolute(this.classesDir);
		List<File> sourceFiles = FileUtility.deepList(srcPath, this.sourceFileFilter);
		
		// Only files that have changed
		this.sourceFiles = sourceFiles.stream().filter(f -> {
			Optional<FileTime> timestamp = FileUtility.timestamp(f);
			File outFile = FileUtility.changeExtension(FileUtility.concat(classPath, FileUtility.relative(f, srcPath)), "class");
			Optional<FileTime> lasttime = FileUtility.timestamp(outFile);
			if (timestamp.isEmpty() || lasttime.isEmpty()) return true;
			if (lasttime.get().compareTo(timestamp.get()) < 0) return true;
			return false;
		}).toList();
		
		return !this.sourceFiles.isEmpty();
		
	}
	
	@Override
	public TaskState prepare() {
		boolean hasToRun = filterSourceFiles();
		return hasToRun ? TaskState.OUTDATED : TaskState.UPTODATE;
	}
	
	@Override
	public boolean run() {
		
		if (!findJavaCompiler()) {
			throw BuildException.msg("could not find java compiler, searched in: %s", new File(this.jdkHome, "bin"));
		}
		
		File binPath = FileUtility.absolute(this.classesDir);
		String[] sourcePaths = this.sourceFiles.stream().map(this::makePathArg).toArray(String[]::new);
		
		logger().infot(logTag(), "compile java source files: %s", this.sourcesDir);
		
		// Init arguments
		this.arguments.addAll(Arrays.asList("-d",  makePathArg(binPath)));
		this.arguments.addAll(Arrays.asList(sourcePaths));
		
		// Run command
		return super.run();
		
	}
	
}
