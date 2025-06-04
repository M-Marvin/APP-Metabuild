package de.m_marvin.metabuild.cpp.tasks;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.CommandLineTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.core.util.HashUtility;

public class CppCompileTask extends CommandLineTask {

	public File sourcesDir = new File("src");
	public Predicate<File> sourcePredicate = file -> {
		String extension = FileUtility.getExtension(file);
		return extension.equalsIgnoreCase("cpp") || extension.equalsIgnoreCase("c");
	};
	public File objectsDir = new File("objects");
	public final List<File> includes = new ArrayList<File>();
	public File stateCache = null; // if not set, will be set by prepare
	public final List<String> options = new ArrayList<>();
	public String sourceStandard = null; // if not set, let the compiler decide
	public String compiler = "g++";

	protected Map<File, File> sourceMetadata = new HashMap<>();
	protected Queue<File> compile;
	protected List<File> removed;
	
	public CppCompileTask(String name) {
		super(name);
		this.type = TaskType.named("COMPILE_CPP");
	}

	protected File getMetaFile() {
		return FileUtility.absolute(this.stateCache, FileUtility.absolute(this.objectsDir));
	}
	
	// TODO XML format for object metadata
	protected void loadMetadata() {
		this.sourceMetadata = new HashMap<>();
		try {
			File metaFile = getMetaFile();
			if (!metaFile.isFile()) return;
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(metaFile)));
			String line;
			while ((line = reader.readLine()) != null) {
				File sourceFile = new File(line);
				File objectFile = new File(reader.readLine());
				this.sourceMetadata.put(sourceFile, objectFile);
			}
			reader.close();
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to load object meta data: %s", this.stateCache);
		}
	}
	
	protected void saveMetadata() {
		try {
			Writer writer = new OutputStreamWriter(new FileOutputStream(getMetaFile()));
			for (var entry : this.sourceMetadata.entrySet()) {
				writer.write(entry.getKey().getPath() + "\n");
				writer.write(entry.getValue().getPath() + "\n");
			}
			writer.close();
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to save object meta data: %s", this.stateCache);
		}
	}
	
	@Override
	protected TaskState prepare() {
		
		// Search for source code files
		File srcPath = FileUtility.absolute(this.sourcesDir);
		List<File> sourceFiles = FileUtility.deepList(srcPath, this.sourcePredicate);
		
		// Generate and load object meta data cache file name if not set
		if (this.stateCache == null) {
			this.stateCache = new File("../" + HashUtility.hash(this.sourcesDir.toString()) + ".objectmeta");
		}
		loadMetadata();
		
		// Check for removed source files
		this.removed = this.sourceMetadata.keySet().stream()
				.filter(f -> !f.isFile())
				.toList();
		
		// Check for source files to compile and create output directories
		this.compile = new ArrayDeque<File>();
		File objPath = FileUtility.absolute(this.objectsDir);
		srcloop: for (File sourceFile : sourceFiles) {
			String unitName = FileUtility.getNameNoExtension(sourceFile);
			Optional<FileTime> sourceTime = FileUtility.timestamp(sourceFile);
			if (sourceTime.isEmpty()) {
				this.compile.add(sourceFile);
				continue;
			}
			File objectDir = FileUtility.absolute(FileUtility.relative(sourceFile, srcPath), objPath).getParentFile();
			if (objectDir.isDirectory()) {
				for (File objectFile : objectDir.listFiles()) {
					if (FileUtility.getNameNoExtension(objectFile).equals(unitName)) {
						Optional<FileTime> objectTime = FileUtility.timestamp(objectFile);
						if (objectTime.isEmpty()) {
							this.compile.add(sourceFile);
							break;
						}
						if (objectTime.get().compareTo(sourceTime.get()) < 0) {
							this.compile.add(sourceFile);
							break;
						}
						continue srcloop;
					}
				}
			}
			this.compile.add(sourceFile);
		}

		// Try to locate compiler executable
		Optional<File> compilerPath = FileUtility.locateOnPath(this.compiler);
		if (compilerPath.isEmpty()) {
			throw BuildException.msg("failed to locate cpp compiler: %s", this.compiler);
		}
		this.executable = compilerPath.get();
		logger().infot(logTag(), "located cpp compiler: %s", this.executable.getAbsolutePath());
		
		return this.compile.size() > 0 ? TaskState.OUTDATED : TaskState.UPTODATE;
		
	}
	
	protected static final Pattern PREPROCESS_PATH_PATTERN = Pattern.compile("#include <\\.\\.\\.> search starts here:([\\S\\s]*)End of search list\\.");
	
	public Collection<File> systemIncludes() {
		
		if (this.executable == null) {
			Optional<File> compilerPath = FileUtility.locateOnPath(this.compiler);
			this.executable = compilerPath.orElse(null);
		}
		if (this.executable == null) return Collections.emptyList();
		
		try {
			ProcessBuilder pbuilder = new ProcessBuilder(this.executable.getAbsolutePath(), "-v", "-xc++", "null");
			pbuilder.redirectErrorStream(true);
			Process process = pbuilder.start();
			ByteArrayOutputStream stdbuf = new ByteArrayOutputStream();
			while (process.isAlive()) {
				stdbuf.write(process.getInputStream().readNBytes(1024));
			}
			String out = new String(stdbuf.toByteArray(), StandardCharsets.UTF_8);
			Matcher m = PREPROCESS_PATH_PATTERN.matcher(out);
			if (!m.find()) return Collections.emptyList();
			return m.group(1).lines().map(String::trim).filter(s -> !s.isBlank()).map(File::new).toList();
		} catch (IOException e) {
			System.err.println("Unable to run cpp command for preprocessor path");
			e.printStackTrace();
		}
		
		return Collections.emptyList();
		
	}
	
	public Collection<File> allIncludes() {
		List<File> includes = new ArrayList<File>();
		includes.addAll(systemIncludes());
		includes.addAll(FileUtility.parseFilePaths(this.includes));
		return includes;
	}
	
	@Override
	public String[] buildCommand() {
		
		File sourceFile = this.compile.poll();
		File srcPath = FileUtility.absolute(this.sourcesDir);
		File objPath = FileUtility.absolute(this.objectsDir);
		
		File objectFile = new File(FileUtility.absolute(FileUtility.relative(sourceFile, srcPath), objPath).getParentFile(), FileUtility.getNameNoExtension(sourceFile) + ".o");
		
		this.sourceMetadata.put(sourceFile, objectFile);
		
		this.arguments.clear();
		
		this.arguments.add("-o");
		this.arguments.add(objectFile.getAbsolutePath());
		this.arguments.add(sourceFile.getAbsolutePath());
		this.arguments.add("-c");
		if (this.sourceStandard != null)
			this.arguments.add("-std=" + this.sourceStandard);
		this.arguments.addAll(this.options);
		
		for (File include : FileUtility.parseFilePaths(this.includes)) {
			File includeDir = FileUtility.absolute(include);
			this.arguments.add("-I\"" + includeDir.getAbsolutePath() + "\"");
		}
		
		return super.buildCommand();
	}
	
	@Override
	public boolean run() {
		
		for (File removedObj : this.removed) {
			this.sourceMetadata.remove(removedObj).delete();
		}
		
		boolean success = true;
		File srcPath = FileUtility.absolute(this.sourcesDir);
		File objPath = FileUtility.absolute(this.objectsDir);
		while (this.compile.size() > 0) {
			File sourceFile = this.compile.peek();
			File objectDir = FileUtility.absolute(FileUtility.relative(sourceFile, srcPath), objPath).getParentFile();
			
			logger().infot(logTag(), "compiling source file: %s", sourceFile.getAbsolutePath());
			
			if (!objectDir.isDirectory() && !objectDir.mkdirs()) {
				throw BuildException.msg("unable to create object output directory: %s", objectDir.getAbsolutePath());
			}

			status("compiling > " + sourceFile.getPath());
			
			if (!super.run()) success = false;
		}

		saveMetadata();
		
		return success;
		
	}
	
}
