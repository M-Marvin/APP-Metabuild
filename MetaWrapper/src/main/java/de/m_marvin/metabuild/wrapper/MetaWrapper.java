package de.m_marvin.metabuild.wrapper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;

public class MetaWrapper {
	
	public static final String META_VERSION_ATTRIBUTE = "Implementation-Version";
	public static final String META_INSTALL_VARIABLE = "META_HOME";
	public static final String DEFAULT_INSTALL_DIRECTORY = System.getProperty("user.home") + "/.meta";
	public static final String META_JAR = "metabuild.jar";
	public static final String META_INSTAL_NAME = "meta-%s";
	
	public static String metaVersion = null;
	public static String metaDirectory = null;
	public static File metaJar = null;
	
	/**
	 * Prepare metabuild installation and run with the supplied command arguments
	 * @param args The command arguments to pass to meta
	 */
	public static void main(String[] args) {
		
		if (!prepareMetabuild())
			System.exit(-1);
		
		System.exit(runMetabuild(args));
		
	}
	
	/**
	 * Prepare metabuild installation, check requirements and install if neccessary.
	 * @return true if the metabuild installation is ready
	 */
	public static boolean prepareMetabuild() {

		try {
			Enumeration<URL> manifests = MetaWrapper.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
			while (manifests.hasMoreElements()) {
				URL url = manifests.nextElement();
				try {
					Manifest manifest = new Manifest(url.openStream());
					metaVersion = manifest.getMainAttributes().getValue("Meta-Version");
					if (metaVersion != null) break;
				} catch (IOException e) {
					System.err.println("\033[31mfailed to read manigest file: " + url);
					e.printStackTrace();
					System.err.print("\033[0m");
				}
			}
		} catch (IOException e) {
			System.err.println("\033[31mfailed to get manifest resources!");
			e.printStackTrace();
			System.err.print("\033[0m");
			return false;
		}
		
		if (metaVersion == null) {
			System.err.println("\033[31mno meta version declared in wrapper manifest!\033[0m");
			return false;
		}
		
		metaDirectory = System.getenv(META_INSTALL_VARIABLE);
		if (metaDirectory == null) metaDirectory = DEFAULT_INSTALL_DIRECTORY;
		
		if (!makeInstall()) {
			System.err.println("\033[31mcould not install metabuild system!\033[0m");
			return false;
		}

		metaJar = new File(metaDirectory, String.format(META_INSTAL_NAME, metaVersion) + "/" + META_JAR);
		
		return true;
		
	}
	
	protected static boolean makeInstall() {
		
		File installDir = new File(metaDirectory, String.format(META_INSTAL_NAME, metaVersion));
		
		if (installDir.isDirectory()) return true;
		
		System.out.println("required meta version: " + metaVersion);
		
		System.err.println("\033[35m\tTODO: AUTO INSTALL NOT IMPLEMENTED!\033[0m");
		
		return false;
		
	}
	
	/**
	 * Run metabuild using the supplied command arguments
	 * @param args The command arguments passed to meta
	 * @return the exit code of the meta runtime
	 */
	public static int runMetabuild(String[] args) {
		
		Optional<String> jvm = ProcessHandle.current().info().command();
		String java = jvm.orElse("java");
		
		List<String> command = new ArrayList<>();
		command.add(java);
		command.add("-jar");
		command.add(metaJar.getAbsolutePath());
		command.addAll(Arrays.asList(args));
		
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.inheritIO();
		try {
			return processBuilder.start().waitFor();
		} catch (InterruptedException | IOException e) {
			System.err.println("\033[31merror occured while starting metabuild!");
			e.printStackTrace();
			System.err.print("\033[0m");
			return -1;
		}
		
	}
	
}
