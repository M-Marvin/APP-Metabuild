package de.m_marvin.metabuild.cpp.tasks;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.CommandLineTask;
import de.m_marvin.metabuild.core.util.FileUtility;

public class CppLinkTask extends CommandLineTask {

	public File objectsDir = new File("objects");
	public File outputFile = null; // if not set, let the compiler decide
	public final Set<File> libraryDirs = new HashSet<File>();
	public final Set<String> libraries = new HashSet<String>();
	public Predicate<File> objectFilePredicate = file -> {
		String extension = FileUtility.getExtension(file);
		return extension.equalsIgnoreCase("o");
	};
	public final List<String> options = new ArrayList<>();
	public String linker = "g++";

	protected List<File> link;
	
	public CppLinkTask(String name) {
		super(name);
		this.type = TaskType.named("LINK_CPP");
	}
	
	@Override
	protected TaskState prepare() {
		
		// Get output timestamp
		File outputFile = FileUtility.absolute(this.outputFile);
		Optional<FileTime> outputTime = FileUtility.timestamp(outputFile);

		// Get all object files to link
		this.link = FileUtility.deepList(FileUtility.absolute(this.objectsDir), this.objectFilePredicate);
		
		// Check if the files have to be linked again
		if (outputTime.isEmpty()) {
			return TaskState.OUTDATED;
		}
		for (File objectFile : this.link) {
			Optional<FileTime> objectTime = FileUtility.timestamp(objectFile);
			if (objectTime.isEmpty()) {
				return TaskState.OUTDATED;
			}
			if (objectTime.get().compareTo(outputTime.get()) > 0) {
				return TaskState.OUTDATED;
			}
		}
		
		return TaskState.UPTODATE;
	}
	
	@Override
	public String[] buildCommand() {
		
		File output = FileUtility.absolute(this.outputFile);
		for (File object : this.link) {
			this.arguments.add(object.getAbsolutePath());
		}
		
		this.arguments.add("-o");
		this.arguments.add(output.getAbsolutePath());
		this.arguments.addAll(this.options);
		
		Set<File> libraryDirectories = new HashSet<File>();
		Set<String> librariesFromPaths = new HashSet<>();
		for (File libPath : FileUtility.parseFilePaths(this.libraryDirs)) {
			if (libPath.isFile()) {
				librariesFromPaths.add(libPath.getName());
				libPath = libPath.getParentFile();
			}
			libraryDirectories.add(libPath);
		}
		libraryDirectories.forEach(d -> this.arguments.add("-L" + d.getAbsolutePath()));
		librariesFromPaths.forEach(l -> this.arguments.add("-l" + l));
		this.libraries.forEach(l -> this.arguments.add("-l" + l));

		// Try to locate compiler executable
		Optional<File> linkerPath = FileUtility.locateOnPath(this.linker);
		if (linkerPath.isEmpty()) {
			logger().warnt(logTag(), "unable to locate cpp linker, linking will fail: %s", this.linker);
		} else {
			this.executable = linkerPath.get();
			logger().infot(logTag(), "located cpp compiler: %s", this.executable.getAbsolutePath());
		}
		
		return super.buildCommand();
	}
	
	@Override
	public boolean run() {
		
		// Try to locate linker executable
		if (this.executable == null) {
			Optional<File> linkerPath = FileUtility.locateOnPath(this.linker);
			this.executable = linkerPath.orElse(null);
		}
		if (this.executable == null)
			throw BuildException.msg("failed to locate cpp linker: %s", this.linker);
		
		logger().infot(logTag(), "linking objects in: %s", this.objectsDir.getAbsolutePath());
		
		// Try to create output directory
		File outputFile = FileUtility.absolute(this.outputFile);
		File outputDir = outputFile.getParentFile();
		if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
			throw BuildException.msg("unable to create output directory: %s", outputDir.getAbsolutePath());
		}
		
		return super.run();
	}

}
