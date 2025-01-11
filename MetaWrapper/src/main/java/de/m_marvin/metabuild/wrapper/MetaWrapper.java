package de.m_marvin.metabuild.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.m_marvin.metabuild.api.core.IMeta;

public class MetaWrapper {
	
	protected static final String META_VERSION_ATTRIBUTE = "Implementation-Version";
	protected static final Pattern META_VERSIONS_CFG_PATTERN = Pattern.compile("([\\w\\d\\.\\-]+)\\s+=\\s+(\\S+)");
	protected static final String META_VERSIONS_CFG = "meta_versions.cfg";
	
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
		
		// Search for wrapper argument
		int vari = -1;
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].matches("-{1,2}wrapper-version")) {
				metaVersion = args[i + 1];
				vari = i;
				break;
			}
		}
		
		// Remove wrapper argument
		if (vari >= 0) {
			String[] a = new String[args.length - 2];
			for (int i = 0; i < a.length; i++)
				a[i] = args[i < vari ? i : i + 2];
			args = a;
		}
		
		if (!prepareMetabuild())
			System.exit(-1);
		
		System.exit(runMetabuild(args));
		
	}
	
	/**
	 * Prepare metabuild installation, check requirements and install if neccessary.
	 * @return true if the metabuild installation is ready
	 */
	public static boolean prepareMetabuild() {

		if (metaVersion == null) {
			
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
	
	protected static URL searchIncluded() {
		try {
			InputStream includedVersions = MetaWrapper.class.getClassLoader().getResourceAsStream(META_VERSIONS_CFG);
			String cfg = new String(includedVersions.readAllBytes(), StandardCharsets.UTF_8);
			includedVersions.close();
			
			Matcher m = META_VERSIONS_CFG_PATTERN.matcher(cfg);
			while (m.find()) {
				String version = m.group(1);
				if (!version.equals(metaVersion)) continue;
				String urlStr = m.group(2);
				try {
					URL url = new URL(urlStr);
					
					System.out.println("\033[35mlocation entry for meta version: " + url + "\033[0m");
					// TODO
					
				} catch (MalformedURLException e) {
					System.err.println("\033[35minvalid URL entry: " + urlStr + "\033[0m");
				}
			}
			
			System.err.println("\033[35mno entry for meta version: " + metaVersion + ", consider updating wrapper\033[0m");
			return null;
		} catch (IOException e) {
			System.err.println("\033[35mcould not access file: " + META_VERSIONS_CFG + "\033[0m");
			return null;
		}
	}
	
	protected static boolean makeInstall() {
		
		File installDir = new File(metaDirectory, String.format(META_INSTAL_NAME, metaVersion));
		
		if (installDir.isDirectory()) return true;
		
		System.out.println("required meta version: " + metaVersion);
		
		URL versionLocation = searchIncluded();
		if (versionLocation == null) return false;
		
		System.err.println("\033[35m\tTODO: AUTO INSTALL NOT IMPLEMENTED!\033[0m");
		
		return false;
		
	}
	
	/**
	 * Tries to dynamically load an metabuild instance from the resolved installation jar file.
	 * @return
	 */
	public static IMeta getMetabuild() {
		try {
			ClassLoader loader = new URLClassLoader(new URL[] { 
					new URL(String.format("jar:file:%s!/", metaJar.getAbsolutePath()))
			}, MetaWrapper.class.getClassLoader());
			
			return IMeta.instantiateMeta(loader);
		} catch (MalformedURLException | InstantiationException e) {
			throw new LayerInstantiationException("could not get metabuild instance!", e);
		}
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
