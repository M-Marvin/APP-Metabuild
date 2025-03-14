package de.m_marvin.metabuild.cpp.tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.CommandLineTask;
import de.m_marvin.metabuild.core.util.FileUtility;

public class CppCompileTask extends CommandLineTask {

	public File sourcesDir = new File("src/cpp");
	public File objectsDir = new File("objects");
	public final Set<File> includeDirs = new HashSet<>();
	public Predicate<File> sourceFilePredicate = file -> {
		String extension = FileUtility.getExtension(file);
		return extension.equalsIgnoreCase("cpp") || extension.equalsIgnoreCase("c");
	};
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
		List<File> sourceFiles = FileUtility.deepList(srcPath, this.sourceFilePredicate);

		// Generate and load object meta data cache file name if not set
		if (this.stateCache == null) {
			String hash = "";
			try {
				ByteBuffer buf = ByteBuffer.wrap(MessageDigest.getInstance("MD5").digest(this.sourcesDir.getPath().getBytes(StandardCharsets.UTF_8)));
				hash = Stream.generate(() -> buf.get()).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
			} catch (NoSuchAlgorithmException e) {
				logger().warnt(logTag(), "failed to generate hash name for object state cache: %s", this.objectsDir, e);
			}
			this.stateCache = new File("../" + hash + ".objectmeta");
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
		
		return this.compile.size() > 0 ? TaskState.OUTDATED : TaskState.UPTODATE;
		
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
		
		for (File include : this.includeDirs) {
			File includeDir = FileUtility.absolute(include);
			this.arguments.add("-I\"" + includeDir.getAbsolutePath() + "\"");
		}
		
		return super.buildCommand();
	}
	
	@Override
	public boolean run() {

		// Try to locate compiler executable
		Optional<File> compilerPath = FileUtility.locateOnPath(this.compiler);
		if (compilerPath.isEmpty()) {
			throw BuildScriptException.msg("failed to locate cpp compiler: %s", this.compiler);
		}
		this.executable = compilerPath.get();
		logger().infot(logTag(), "located cpp compiler: %s", this.executable.getAbsolutePath());
		
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
				throw BuildScriptException.msg("unable to create object output directory: %s", objectDir.getAbsolutePath());
			}
			
			if (!super.run()) success = false;
		}

		saveMetadata();
		
		return success;
		
	}
	
}
