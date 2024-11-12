package de.m_marvin.metabuild.core.script.compile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class SingleFileClassLoader extends ClassLoader {

	protected ClassLoader parentLoader;
	protected byte[] classBytes;
	protected String className;
	
	public SingleFileClassLoader(ClassLoader parentLoader, String className, byte[] classBytes) {
		this.parentLoader = parentLoader;
		this.classBytes = classBytes;
		this.className = className;
	}
	
	public SingleFileClassLoader(ClassLoader parentLoader, String className, InputStream classStream) throws IOException {
		this(parentLoader, className, classStream.readAllBytes());
	}
	
	public SingleFileClassLoader(ClassLoader parentLoader, String className, File classFile) throws FileNotFoundException, IOException {
		this(parentLoader, className, new FileInputStream(classFile));
	}
	
	public SingleFileClassLoader(ClassLoader parentLoader, File classFile) throws FileNotFoundException, IOException {
		this(parentLoader, classFile.getName().substring(0, classFile.getName().lastIndexOf('.')), classFile);
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (this.className.equals(name)) {
			return defineClass(name, this.classBytes, 0, this.classBytes.length);
		}
		if (this.parentLoader == null) throw new ClassNotFoundException("could not find class in file: " + name);
		return this.parentLoader.loadClass(name);
	}
	
}
