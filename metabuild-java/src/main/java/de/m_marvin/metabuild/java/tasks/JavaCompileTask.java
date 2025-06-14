package de.m_marvin.metabuild.java.tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.XMLStream.DescType;
import de.m_marvin.basicxml.XMLStream.ElementDescriptor;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.tasks.BuildTask;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.metabuild.core.util.HashUtility;

public class JavaCompileTask extends BuildTask {

	public File sourcesDir = new File("src/java");
	public File classesDir = new File("classes/default");
	public File headersDir = null;
	public final List<String> options = new ArrayList<>();
	public final List<File> classpath = new ArrayList<>();
	public File stateCache = null; // if not set, will be set by prepare
	public String sourceCompatibility = null;
	public String targetCompatibility = null;
	
	public static record SourceMetaData(File[] classFiles, FileTime timestamp) { }
	
	protected JavaCompiler javac;
	protected Map<File, SourceMetaData> sourceMetadata = new HashMap<>();
	protected List<File> compile;
	protected List<File> removed;
	
	public JavaCompileTask(String name) {
		super(name);
		this.type = TaskType.named("JAVA_COMPILE");
	}
	
	protected File getMetaFile() {
		return FileUtility.absolute(this.stateCache, FileUtility.absolute(this.classesDir));
	}

	public static final URI METABUILD_JAVA_CLASSMETA_NAMESPACE = URI.create("https://github.com/M-Marvin/APP-Metabuild/java/classmeta");
	
	protected void loadMetadata() {
		this.sourceMetadata = new HashMap<>();
		try {
			File metaFile = getMetaFile();
			if (!metaFile.isFile()) return;
			
			XMLInputStream xml = new XMLInputStream(new FileInputStream(metaFile));
			ElementDescriptor classmetaTag = xml.readNext();
			if (classmetaTag != null && classmetaTag.name().equals("classmeta") && classmetaTag.type() == DescType.OPEN) {
				ElementDescriptor classfileTag;
				while ((classfileTag = xml.readNext()) != null) {
					if (classfileTag != null && classfileTag.name().equals("classfile") && classfileTag.type() == DescType.OPEN) {
						List<File> classFiles = new ArrayList<File>();
						ElementDescriptor classTag;
						while ((classTag = xml.readNext()) != null) {
							if (classTag != null && classTag.name().equals("class") && classTag.type() == DescType.OPEN) {
								classFiles.add(new File(xml.readAllText()));
								ElementDescriptor classTagClose = xml.readNext();
								if (!classTagClose.isSameField(classTag) || classTagClose.type() != DescType.CLOSE) {
									this.sourceMetadata.clear();
									xml.close();
									return;
								}
							} else if (classTag != null && classTag.name().equals("classfile") && classTag.type() == DescType.CLOSE) {
								break;
							}
						}
						
						if (!classfileTag.attributes().containsKey("source") || !classfileTag.attributes().containsKey("timestamp")) {
							this.sourceMetadata.clear();
							xml.close();
							return;
						}
						
						try {
							File sourceFile = new File(classfileTag.attributes().get("source"));
							FileTime timestamp = FileTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(classfileTag.attributes().get("timestamp"))));
							this.sourceMetadata.put(sourceFile, new SourceMetaData(classFiles.toArray(File[]::new), timestamp));
						} catch (Exception e) {
							this.sourceMetadata.clear();
							xml.close();
							return;
						}
						
					} else if (classfileTag != null && classfileTag.name().equals("classmeta") && classfileTag.type() == DescType.CLOSE) {
						break;
					}
				}
			}
			xml.close();
		} catch (Exception e) {
			throw BuildException.msg(e, "failed to load class meta data: %s", this.stateCache);
		}
	}
	
	protected void saveMetadata() {
		try {
			XMLOutputStream xml = new XMLOutputStream(new FileOutputStream(getMetaFile()));
			xml.writeNext(new ElementDescriptor(DescType.OPEN, METABUILD_JAVA_CLASSMETA_NAMESPACE, "classmeta", null));
			for (var entry : this.sourceMetadata.entrySet()) {
				var tag = new ElementDescriptor(DescType.OPEN, METABUILD_JAVA_CLASSMETA_NAMESPACE, "classfile", new HashMap<String, String>());
				tag.attributes().put("timestamp", entry.getValue().timestamp().toString());
				tag.attributes().put("source", entry.getKey().getPath());
				xml.writeNext(tag);
				for (var cfile : entry.getValue().classFiles()) {
					xml.writeNext(new ElementDescriptor(DescType.OPEN, METABUILD_JAVA_CLASSMETA_NAMESPACE, "class", null));
					xml.writeAllText(cfile.getPath(), false);
					xml.writeNext(new ElementDescriptor(DescType.CLOSE, METABUILD_JAVA_CLASSMETA_NAMESPACE, "class", null));
				}
				xml.writeNext(new ElementDescriptor(DescType.CLOSE, METABUILD_JAVA_CLASSMETA_NAMESPACE, "classfile", null));
			}
			xml.writeNext(new ElementDescriptor(DescType.CLOSE, METABUILD_JAVA_CLASSMETA_NAMESPACE, "classmeta", null));
			xml.close();
		} catch (Exception e) {
			throw BuildException.msg(e, "failed to save class meta data: %s", this.stateCache);
		}
	}
	
	@Override
	public TaskState prepare() {
		
		File srcPath = FileUtility.absolute(this.sourcesDir);
		
		if (this.stateCache == null)
			this.stateCache = new File("../" + HashUtility.hash(this.sourcesDir.getPath()) + ".classmeta");
		
		loadMetadata();
		
		for (File path : FileUtility.parseFilePaths(this.classpath)) {
			
			if (path.isFile()) {
				
				// Check for changed classpath file
				Optional<SourceMetaData> oldest = this.sourceMetadata.size() > 0 ? this.sourceMetadata.values().stream().sorted((s, b) -> s.timestamp().compareTo(b.timestamp())).skip(this.sourceMetadata.size() - 1).findFirst() : Optional.empty();
				Optional<FileTime> classpath = FileUtility.timestamp(path);
				if (oldest.isEmpty() || classpath.isEmpty() || classpath.get().compareTo(oldest.get().timestamp()) > 0) {
					this.sourceMetadata.clear(); // clearing the metadata causes all files to be recompiled
					break;
				}
				
			}
			
		}
		
		List<File> sourceFiles = FileUtility.deepList(srcPath, f -> FileUtility.getExtension(f).equalsIgnoreCase("java")).stream().
				map(f -> FileUtility.relative(f, srcPath))
				.toList();
		
		// Check for updated source files to compile
		this.compile = sourceFiles.stream()
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
		this.removed = this.sourceMetadata.entrySet().stream()
			.filter(e -> !sourceFiles.contains(e.getKey()))
			.map(e -> e.getKey())
			.toList();
		
		return (!this.compile.isEmpty() || !this.removed.isEmpty()) ? TaskState.OUTDATED : TaskState.UPTODATE;
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
		
		try {
			
			DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
			CompilationOutputFileManager fileManager = new CompilationOutputFileManager(javac.getStandardFileManager(diagnostics, null, null));
			CompilationTask task = javac.getTask(logger().errorWriter(), fileManager, diagnostics, this.options, null, Collections.singleton(sourceFileObject));
			
			boolean success = task.call();
			
			for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
				String info = null;
				
				try {
					long lineNr = diagnostic.getLineNumber();
					
					String line = "<not available>";
					if (diagnostic.getSource() != null) {
						BufferedReader reader = new BufferedReader(new InputStreamReader(diagnostic.getSource().openInputStream()));
						for (long i = 0; i < lineNr; i++) line = reader.readLine();
						reader.close();
					}
					
					StringBuffer buff = new StringBuffer();
					
					if (diagnostic.getLineNumber() < 0) {
						buff.append(diagnostic.getMessage(Locale.ENGLISH));
					} else {
						buff.append(line).append("\n");
						for (long i = 0; i < diagnostic.getColumnNumber() - 1; i++) buff.append(" ");
						buff.append("^ Here: ").append(diagnostic.getMessage(Locale.ENGLISH));
					}
					
					info = buff.toString();
				} catch (IOException e) {
					info = "source line unavailable";
				}
				
				if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
					if (diagnostic.getLineNumber() >= 0) {
						logger().errort(logTag(), "[%s] : Line %d / Col %d\n%s", diagnostic.getKind().name(), diagnostic.getLineNumber(), diagnostic.getColumnNumber(), info);
					} else {
						logger().errort(logTag(), "[%s] : %s", diagnostic.getKind().name(), info);
					}
				} else if (diagnostic.getKind() == Diagnostic.Kind.WARNING || diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
					if (diagnostic.getLineNumber() >= 0) {
						logger().warnt(logTag(), "[%s] : Line %d / Col %d\n%s", diagnostic.getKind().name(), diagnostic.getLineNumber(), diagnostic.getColumnNumber(), info);
					} else {
						logger().warnt(logTag(), "[%s] : %s", diagnostic.getKind().name(), info);
					}
				} else {
					if (diagnostic.getLineNumber() >= 0) {
						logger().infot(logTag(), "[%s] : Line %d / Col %d\n%s", diagnostic.getKind().name(), diagnostic.getLineNumber(), diagnostic.getColumnNumber(), info);
					} else {
						logger().infot(logTag(), "[%s] : %s", diagnostic.getKind().name(), info);
					}
				}
				
			}
			
			if (!success) return Optional.empty();
			return Optional.of(fileManager.getClassFilesOut());
			
		} catch (Exception e) {
			throw BuildException.msg(e, "unexpected error whenn invoking java compiler: ", e.getMessage());
		}
		
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
		for (File outdatedSource : this.removed) {
			for (File outdatedClass : this.sourceMetadata.get(outdatedSource).classFiles()) {
				File f = FileUtility.absolute(outdatedClass, classPath);
				logger().debugt(logTag(), "delete from removed source: %s", outdatedClass);
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
		
		// Add header directory to compiler options
		if (this.headersDir != null) {
			File headerFolder = FileUtility.absolute(this.headersDir);
			headerFolder.mkdirs();
			this.options.add("-h");
			this.options.add(headerFolder.getAbsolutePath());
		}
		
		// Add compatibility options
		if (this.sourceCompatibility != null) {
			this.options.add("-source");
			this.options.add(this.sourceCompatibility);
		}
		if (this.targetCompatibility != null) {
			this.options.add("-target");
			this.options.add(this.targetCompatibility);	
		}
		
		// Add classpath options
		StringBuffer classpathBuf = new StringBuffer();
		
		String classpathStr = FileUtility.parseFilePaths(this.classpath).stream().map(File::getAbsolutePath).reduce((a, b) -> a + ";" + b).orElse("");
		classpathBuf.append(classpathStr);
		classpathBuf.append(";" + FileUtility.absolute(this.sourcesDir));
		this.options.add("-classpath");
		this.options.add(classpathBuf.toString());
		
		this.options.add("-sourcepath");
		this.options.add(FileUtility.absolute(this.sourcesDir).toString());
		
		logger().debugt(logTag(), "compillation options: %s", this.options.stream().reduce((a, b) -> a + " " + b).orElse("<empty>"));
		logger().infot(logTag(), "compiling source files in: %s", this.sourcesDir);
		
		// Compile outdated files
		for (File sourceFile : this.compile) {

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
