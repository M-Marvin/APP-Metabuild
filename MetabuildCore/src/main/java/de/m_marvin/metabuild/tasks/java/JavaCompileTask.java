package de.m_marvin.metabuild.tasks.java;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.tasks.BuildTask;

public class JavaCompileTask extends BuildTask {

	public File sourcesDir = new File("src/java");
	public File classesDir = new File("classes/default");
	public final List<String> options = new ArrayList<>();
	public File classpath = null;
	public File stateCache = null; // if not set, will be set by prepare
	
	public static record SourceMetaData(File[] classFiles, FileTime timestamp) { }
	
	protected JavaCompiler javac;
	protected Map<File, SourceMetaData> sourceMetadata = new HashMap<>();
	protected List<File> toCompile;
	protected List<File> toRemove;
	
	public JavaCompileTask(String name) {
		super(name);
		this.type = TaskType.named("JAVA_COMPILE");
	}
	
	protected File getMetaFile() {
		return FileUtility.absolute(this.stateCache, FileUtility.absolute(this.classesDir));
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
				FileTime timestamp = FileTime.fromMillis(Long.parseLong(reader.readLine()));
				File[] classFiles = Stream.of(reader.readLine().split(";")).map(File::new).toArray(File[]::new);
				this.sourceMetadata.put(sourceFile, new SourceMetaData(classFiles, timestamp));
			}
			reader.close();
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to load class meta data: %s", this.stateCache);
		}
	}
	
	protected void saveMetadata() {
		try {
			Writer writer = new OutputStreamWriter(new FileOutputStream(getMetaFile()));
			for (var entry : this.sourceMetadata.entrySet()) {
				writer.write(entry.getKey().getPath() + "\n");
				writer.write(Long.toString(entry.getValue().timestamp().toMillis()) + "\n");
				writer.write(Stream.of(entry.getValue().classFiles()).map(File::getPath).reduce((a, b) -> a + ";" + b).get() + "\n");
			}
			writer.close();
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to save class meta data: %s", this.stateCache);
		}
	}
	
	@Override
	public TaskState prepare() {
		
		File srcPath = FileUtility.absolute(this.sourcesDir);
		
		if (this.stateCache == null) {
			String hash = "";
			try {
				ByteBuffer buf = ByteBuffer.wrap(MessageDigest.getInstance("MD5").digest(this.sourcesDir.getPath().getBytes(StandardCharsets.UTF_8)));
				hash = Stream.generate(() -> buf.get()).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
			} catch (NoSuchAlgorithmException e) {
				logger().warnt(logTag(), "failed to generate hash name for class state cache: %s", this.classesDir, e);
			}
			this.stateCache = new File("../" + hash + ".classmeta");
		}
		
		loadMetadata();

		// Check for changed classpath file
		Optional<SourceMetaData> oldest = this.sourceMetadata.size() > 0 ? this.sourceMetadata.values().stream().sorted((s, b) -> s.timestamp().compareTo(b.timestamp())).skip(this.sourceMetadata.size() - 1).findFirst() : Optional.empty();
		Optional<FileTime> classpath = FileUtility.timestamp(FileUtility.absolute(this.classpath));
		if (oldest.isEmpty() || classpath.isEmpty() || classpath.get().compareTo(oldest.get().timestamp()) > 0)
			this.sourceMetadata.clear(); // clearing the metadata causes all files to be recompiled
		
		List<File> sourceFiles = FileUtility.deepList(srcPath, f -> FileUtility.getExtension(f).equalsIgnoreCase("java")).stream().
				map(f -> FileUtility.relative(f, srcPath))
				.toList();
		
		// Check for updated source files to compile
		this.toCompile = sourceFiles.stream()
			.filter(f -> f.getName().endsWith(".java"))
			.filter(f -> {
				if (!this.sourceMetadata.containsKey(f)) return true;
				Optional<FileTime> a = FileUtility.timestamp(FileUtility.absolute(f, srcPath));
				if (a.isEmpty()) return true;
				if (a.get().compareTo(this.sourceMetadata.get(f).timestamp()) > 0) return true;
				return false;
			})
			.toList();
		
		// List class files that need to be removed
		this.toRemove = this.sourceMetadata.entrySet().stream()
			.filter(e -> !sourceFiles.contains(e.getKey()))
			.map(e -> e.getKey())
			.toList();
		
		return (!this.toCompile.isEmpty() || !this.toRemove.isEmpty()) ? TaskState.OUTDATED : TaskState.UPTODATE;
	}
	
	protected class CompilationOutputFileManager extends ForwardingJavaFileManager<JavaFileManager> {
		
		protected List<File> classFilesOut = new ArrayList<>();
		
		protected CompilationOutputFileManager(JavaFileManager fileManager) {
			super(fileManager);
		}
		
		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
			JavaFileObject f = super.getJavaFileForOutput(location, className, kind, sibling);
			this.classFilesOut.add(Path.of(f.toUri()).toFile());
			return f;
		}
		
		public List<File> getClassFilesOut() {
			return classFilesOut;
		}
		
	}
	
	protected static class FileJavaSource extends SimpleJavaFileObject {
		
		public FileJavaSource(String name, File sourceFile) {
			super(sourceFile.toURI(), Kind.SOURCE);
		}
		
		@Override
		public InputStream openInputStream() throws IOException {
			return new FileInputStream(Path.of(uri).toFile());
		}
		
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			InputStream istream = openInputStream();
			String content = new String(istream.readAllBytes());
			istream.close();
			return content;
		}
		
	}
	
	protected boolean findJavaCompiler() {
		this.javac = ToolProvider.getSystemJavaCompiler();
		return this.javac != null;
	}
	
	protected Optional<List<File>> compileSource(File source) {
		
		String sourceName = FileUtility.getNameNoExtension(source);
		SimpleJavaFileObject sourceFileObject = new FileJavaSource(sourceName, source);
		
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		CompilationOutputFileManager fileManager = new CompilationOutputFileManager(javac.getStandardFileManager(diagnostics, null, null));
		CompilationTask task = javac.getTask(logger().errorWriter(), fileManager, diagnostics, this.options, null, Collections.singleton(sourceFileObject));
		
		if (!task.call()) {
			logger().warnt(logTag(), "problems occured while compiling file: %s", source);
			for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
				String info = null;
				
				try {
					long lineNr = diagnostic.getLineNumber();
					
					BufferedReader reader = new BufferedReader(new InputStreamReader(diagnostic.getSource().openInputStream()));
					String line = "";
					for (long i = 0; i < lineNr; i++) line = reader.readLine();
					reader.close();
					
					StringBuffer buff = new StringBuffer();
					buff.append(line).append("\n");
					for (long i = 0; i < diagnostic.getColumnNumber() - 1; i++) buff.append(" ");
					buff.append("^ Here: ").append(diagnostic.getMessage(null));
					
					info = buff.toString();
				} catch (IOException e) {
					info = "source line unavailable";
				}
				
				logger().warnt(logTag(), "[%s] : Line %d / Col %d\n%s", diagnostic.getKind().name(), diagnostic.getLineNumber(), diagnostic.getColumnNumber(), info);
			}
			return Optional.empty();
		}
		
		return Optional.of(fileManager.getClassFilesOut());
		
	}
	
	@Override
	public boolean run() {

		if (!findJavaCompiler()) {
			logger().errort(logTag(), "failed to find java compiler!");
			return false;
		}
		
		File srcPath = FileUtility.absolute(this.sourcesDir);
		File classPath = FileUtility.absolute(this.classesDir);
		
		// Delete outdated class files
		for (File outdatedSource : this.toRemove) {
			for (File outdatedClass : this.sourceMetadata.get(outdatedSource).classFiles()) {
				File f = FileUtility.absolute(outdatedClass, classPath);
				if (!f.delete()) {
					logger().errort(logTag(), "could not remove outdated class file: %s", outdatedClass);
					return false;
				}
			}
			this.sourceMetadata.remove(outdatedSource);
		}
		
		// Add output directory to compiler options
		this.options.add("-d");
		this.options.add(classPath.getAbsolutePath());
		
		// Add classpath file content to options if set
		if (this.classpath != null) {
			File classpathFile = FileUtility.absolute(this.classpath);
			if (!classpathFile.isFile()) {
				logger().warnt(logTag(), "classpath file not found: %s", this.classpath);
			} else {
				try {
					InputStream is = new FileInputStream(classpathFile);
					String classpath = new String(is.readAllBytes(), StandardCharsets.UTF_8);
					is.close();
					this.options.add("-classpath");
					this.options.add(classpath);
				} catch (IOException e) {
					throw BuildException.msg(e, "could not access classpath file: %s", this.classpath);
				}
			}
		}
		
		// Compile outdated files
		for (File sourceFile : this.toCompile) {

			status("compiling > " + sourceFile.getPath());
			
			logger().debugt(logTag(), "compiling source file: %s", sourceFile);
			
			File file = FileUtility.absolute(sourceFile, srcPath);
			Optional<List<File>> classFiles = compileSource(file);
			if (classFiles.isEmpty()) {
				logger().errort(logTag(), "failed to compile java source: %s", sourceFile);
				return false;
			}
			
			// Update metadata and remove possible outdated class files
			SourceMetaData newMeta = new SourceMetaData(classFiles.get().toArray(File[]::new), FileUtility.timestamp(classFiles.get().get(0)).get());
			SourceMetaData oldMeta = this.sourceMetadata.put(sourceFile, newMeta);
			if (oldMeta != null) {
				List<File> outdatedFiles = Stream.of(oldMeta.classFiles()).filter(f -> !Arrays.asList(newMeta.classFiles()).contains(f)).toList();
				for (File outdatedFile : outdatedFiles) {
					File f = FileUtility.absolute(outdatedFile, classPath);
					if (f.isFile() && !f.delete()) {
						logger().errort(logTag(), "failed to remove outdated class file: %s", outdatedFile);
						return false;
					}
				}
			}
		}
		
		saveMetadata();
		
		return true;
	}
	
}
