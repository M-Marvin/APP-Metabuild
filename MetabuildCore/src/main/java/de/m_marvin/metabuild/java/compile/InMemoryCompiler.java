package de.m_marvin.metabuild.java.compile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

public class InMemoryCompiler {

	protected final JavaCompiler compiler;
	protected final InMemoryFileManager classFileManager;
	protected final DiagnosticCollector<JavaFileObject> diagnostics;
	protected PrintWriter out = new PrintWriter(System.out, true);
	
	public InMemoryCompiler() {
		this(ToolProvider.getSystemJavaCompiler());
	}
	
	public InMemoryCompiler(JavaCompiler compiler) {
		
		ToolProvider.getSystemJavaCompiler();
		
		Objects.requireNonNull(compiler);
		this.compiler = compiler;
		this.classFileManager = new InMemoryFileManager(this.compiler.getStandardFileManager(null, null, null));
		this.diagnostics = new DiagnosticCollector<>();
	}
	
	public void setOut(PrintWriter out) {
		this.out = out;
	}
	
	public InMemoryFileManager getClassFileManager() {
		return classFileManager;
	}
	
	public boolean compile(String className, String classCode, String classpath) {
		Iterable<JavaFileObject> sourceFiles = Collections.singleton(new StringJavaSource(className, classCode));
		List<String> optionList = new ArrayList<String>();
		optionList.addAll(Arrays.asList("-classpath",System.getProperty("java.class.path") + ";" + classpath));
		CompilationTask task = compiler.getTask(new PrintWriter(out), classFileManager, diagnostics, optionList, null, sourceFiles);
		if (task.call()) {
			return true;
		} else {
			diagnostics.getDiagnostics().forEach(diag -> this.out.println(diag.toString()));
			return false;
		}
	}
	
	public static class StringJavaSource extends SimpleJavaFileObject {

		protected String sourceCode;
		
		protected StringJavaSource(String name, String sourceCode) {
			super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.sourceCode = sourceCode;
		}
		
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			return this.sourceCode;
		}
		
	}
	
	public static class BytesJavaClass extends SimpleJavaFileObject {
		
		protected ByteArrayOutputStream buffer;
		
		protected BytesJavaClass(String name, Kind kind) {
			super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
			this.buffer = new ByteArrayOutputStream();
		}
		
		@Override
		public OutputStream openOutputStream() throws IOException {
			return this.buffer;
		}
		
		public byte[] getClassBytes() {
			return this.buffer.toByteArray();
		}
		
	}
	
	public static class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

		protected Map<String, BytesJavaClass> name2classMap;
		
		protected InMemoryFileManager(JavaFileManager fileManager) {
			super(fileManager);
			this.name2classMap = new HashMap<>();
		}
		
		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
			BytesJavaClass classObject = new BytesJavaClass(className, kind);
			this.name2classMap.put(className, classObject);
			return classObject;
		}
		
		public Set<String> getCompiledClasses() {
			return this.name2classMap.keySet();
		}
		
		public byte[] getClassBytes(String name) {
			return this.name2classMap.containsKey(name) ? this.name2classMap.get(name).getClassBytes() : null;
		}
		
	}
	
}
