package de.m_marvin.metabuild.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DynamicFileListClassLoader extends ClassLoader {
	
	private final ClassLoader parentLoader;
	private final Map<File, JarFile> files = new HashMap<File, JarFile>();;
	
	public DynamicFileListClassLoader(ClassLoader parent) {
		this(parent, null);
	}
	
	public DynamicFileListClassLoader(ClassLoader parent, Collection<File> files) {
		this.parentLoader = parent;
		if (files != null)
			files.forEach(f -> this.files.put(f, null));
	}
	
	public void addFile(File file) {
		this.files.put(file, null);
	}
	
	public Collection<File> getFiles() {
		return this.files.keySet();
	}
	
	public void close() throws IOException {
		IOException ex = new IOException("one ore more files could not be closed");
		for (JarFile jar : this.files.values()) {
			try {
				if (jar != null) jar.close();
			} catch (IOException e) {
				ex.addSuppressed(e);
			}
		}
		if (ex.getSuppressed().length > 0) throw ex;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		
		try {
			for (File file : this.files.keySet()) {
				JarFile jar = this.files.get(file);
				if (jar == null) {
					jar = new JarFile(file);
					this.files.put(file, jar);
				}
				
				JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
				if (entry == null) continue;
				
				InputStream classStream = jar.getInputStream(entry);
				byte[] classBytes = classStream.readAllBytes();
				classStream.close();
				CodeSource codeSource = new CodeSource(file.toURI().toURL(), entry.getCodeSigners());
				PermissionCollection permissions = this.getClass().getProtectionDomain().getPermissions();
				ProtectionDomain domain = new ProtectionDomain(codeSource, permissions, this, new Principal[0]);
				return defineClass(name, classBytes, 0, classBytes.length, domain);
			}
		} catch (IOException e) {}

		return this.parentLoader.loadClass(name);
		
	}
	
}
