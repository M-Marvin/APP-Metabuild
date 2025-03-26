package de.m_marvin.metabuild.core.script.compile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import de.m_marvin.javarun.compile.SourceCompiler;
import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.MetaInitError;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.core.util.FileUtility;
import de.m_marvin.simplelogging.api.Logger;

public class ScriptCompiler {
	
	protected static final String LOG_TAG = "ScriptCompiler";
	
	private final JavaCompiler compiler;
	private final Metabuild mb;
	
	public ScriptCompiler(Metabuild mb) {
		this.mb = mb;
		this.compiler = ToolProvider.getSystemJavaCompiler();
		if (this.compiler == null)
			throw MetaInitError.msg("no compiler for the build script available, install a JDK!");
	}
	
	public File getCompileCache() {
		File buildFileCache = FileUtility.concat(this.mb.cacheDir(), new File("buildfiles"));
		if (!buildFileCache.isDirectory() && !buildFileCache.mkdir())
			throw MetaInitError.msg("failed to create build file cache: %s", buildFileCache.getPath());
		return buildFileCache;
	}
	
	public Logger logger() {
		return this.mb.logger();
	}
	
	public BuildScript loadBuildFile(File buildFile) {
		
		String hash = null;
		try {
			ByteBuffer buf = ByteBuffer.wrap(MessageDigest.getInstance("MD5").digest(buildFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8)));
			hash = Stream.generate(() -> buf.get()).limit(buf.capacity()).mapToInt(b -> b & 0xFF).mapToObj(i -> String.format("%02x", i)).reduce(String::concat).get();
		} catch (NoSuchAlgorithmException e) {
			logger().warnt(LOG_TAG, "could not get message digest instance, this could cause problems!");
			hash = Long.toHexString(buildFile.getAbsolutePath().hashCode());
		}

		File classCache = new File(getCompileCache(), String.format("%s_%s.class",  buildFile.getName().substring(0, buildFile.getName().indexOf('.')), hash));
		Optional<FileTime> buildFileTime = FileUtility.timestamp(buildFile);
		Optional<FileTime> classFileTime = FileUtility.timestamp(classCache);
		
		if (buildFileTime.isEmpty() || classFileTime.isEmpty() ||
			buildFileTime.get().compareTo(classFileTime.get()) > 0) {

			try {
				InputStream source = new FileInputStream(buildFile);
				OutputStream target = new FileOutputStream(classCache);
				
				boolean r = compileBuildScript(source, target);
				
				source.close();
				target.close();
				
				if (r) {
					FileUtility.touch(classCache, buildFileTime.get());
				} else {
					classCache.delete();
					return null;
				}
			} catch (FileNotFoundException e) {
				logger().errort(LOG_TAG, "could not compile buildfile: %s", e.getMessage());
				classCache.delete();
				return null;
			} catch (IOException e) {
				logger().errort(LOG_TAG, "io exception while compiling build file: %s", e.getMessage());
				e.printStackTrace();
				classCache.delete();
				return null;
			}
			
		}
		
		try {
			ClassLoader loader = new SingleFileClassLoader(Thread.currentThread().getContextClassLoader(), Metabuild.BUILD_SCRIPT_CLASS_NAME, classCache);
			Class<?> buildfileClass = loader.loadClass(Metabuild.BUILD_SCRIPT_CLASS_NAME);
			Object buildScriptObject = buildfileClass.getConstructor().newInstance();
			if (buildScriptObject instanceof BuildScript buildScript) return buildScript;
			
			logger().errort(LOG_TAG, "buildfile class does not extend '%s', buildfile invalid!", BuildScript.class.getName());
			return null;
		} catch (NoSuchMethodException e) {
			logger().errort(LOG_TAG, "no zero argument constructor for Buildfile class, buildfile invalid!");
			return null;
		} catch (ClassNotFoundException e) {
			logger().errort(LOG_TAG, "no class named '%s' found, buildfile invalid!", Metabuild.BUILD_SCRIPT_CLASS_NAME);
			return null;
		} catch (IOException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			logger().errort(LOG_TAG, "unknown error while instantiating buildfile class:", e);
			return null;
		} catch (NoClassDefFoundError e) {
			logger().errort(LOG_TAG, "class not found error in buildfile: %s", e.getMessage());
			return null;
		}
	}
	
	public boolean compileBuildScript(InputStream source, OutputStream target) throws IOException {
		
		SourceCompiler sourceCompiler = new SourceCompiler(this.compiler);
		sourceCompiler.setOut(logger().errorPrinter(LOG_TAG));
		
		String classpath = this.mb.getBuildfileClasspath().stream()
				.map(File::getAbsolutePath)
				.reduce((a, b) -> a + ";" + b)
				.orElse("");
		
		if (!sourceCompiler.compile(Metabuild.BUILD_SCRIPT_CLASS_NAME, new String(source.readAllBytes()), classpath)) {
			logger().errort(LOG_TAG, "java compiler error, build file compilation failed!");
			return false;
		}
		
		byte[] classBytes = sourceCompiler.getClassFileManager().getClassBytes(Metabuild.BUILD_SCRIPT_CLASS_NAME);
		if (classBytes == null) {
			logger().errort(LOG_TAG, "java compiler error, build file compilation failed!");
			return false;
		}
		target.write(classBytes);
		
		return true;
		
	}
	
}
