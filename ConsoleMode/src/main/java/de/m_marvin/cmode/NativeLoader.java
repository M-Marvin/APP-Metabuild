package de.m_marvin.cmode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/* NativeLoader v0.1 */
public class NativeLoader {
	
	private static String libLoadConfig = "/libload.cfg";
	private static String tempLibFolder = System.getProperty("java.io.tmpdir");
	private static Map<String, File> libMap = new HashMap<>();
	
	public static void setLibLoadConfig(String fileLocation) {
		libLoadConfig = fileLocation;
	}
	
	public static void setTempLibFolder(String libFolder) {
		tempLibFolder = libFolder;
	}
	
	public static String getArchitectureName() {
		
		// TODO: Check the correct values for os.arch
		String arch = System.getProperty("os.arch").toLowerCase();
		if (arch.contains("amd64")) {
			arch = "amd_64";
		} else if (arch.contains("x86")) {
			arch = "amd_32";
		} else if (arch.contains("arm")) {
			arch = "arm_32";
		} else if (arch.contains("aarch64")) {
			arch = "arm_64";
		} else {
			System.err.println("NativeLoader: Unknown arch: " + arch);
		}
		
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			os = "win";
		} else if (os.contains("linux") || os.contains("sunos") || os.contains("freebsd")) {
			os = "lin";
		} else {
			System.err.println("NativeLoader: Unknown os: " + os);
		}
		
		return os + "_" + arch;
	}
	
	private static String getNativeForArchitecture(String nativeName) {
		String arch = getArchitectureName();
		String nativeFile = null;
		
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(NativeLoader.class.getResourceAsStream(libLoadConfig)));
			String line;
			fileread: while ((line = reader.readLine()) != null) {
				if (line.equals("[" + nativeName + "]")) {
					while ((line = reader.readLine()) != null) {
						String[] entry = line.split("=");
						if (entry.length == 2 && entry[0].contains(arch)) {
							nativeFile = entry[1];
							break fileread;
						}
					}
				}
			}
			reader.close();
		} catch (IOException | NullPointerException e) {
			System.err.println("NativeLoader: Could not open config file!");
		}
		
		if (nativeFile == null) {
			System.err.println("NativeLoader: No natives for the current architecture available!");
		}
		
		return nativeFile;
	}
	
	public static void extractNative(String nativeLocation, String targetLocation, String targetFileName) {
		
		if (!new File(targetLocation).isDirectory()) {
			new File(targetLocation).mkdir();
		}
		
		try {
			InputStream is = NativeLoader.class.getResourceAsStream(nativeLocation);
			if (is == null) {
				System.err.println("NativeLoader: The native lib " + nativeLocation + " is missing in the jar!");
				return;
			}
			OutputStream os = new FileOutputStream(new File(targetLocation, targetFileName));
			os.write(is.readAllBytes());
			is.close();
			os.close();
		} catch (FileNotFoundException e) {
			System.out.println("Could not extract native " + targetFileName + ", maybe it already exists and is used by an another process!");
		} catch (IOException e) {
			System.err.println("NativeLoader: Failed to extract native " + nativeLocation + " to " + targetLocation + "!");
			e.printStackTrace();
		}
	}
	
	public static String getNative(String nativeName) {
		
		if (!libMap.containsKey(nativeName)) {
			String nativeFilePath = getNativeForArchitecture(nativeName);
			if (nativeFilePath == null) {
				System.err.println("NativeLoader: Unable to extract native " + nativeFilePath + "!");
				return null;
			}
			String fileName = new File(nativeFilePath).getName();
			File tempLocation = new File(tempLibFolder, fileName);
			libMap.put(nativeName, tempLocation);
			
			extractNative(nativeFilePath, tempLibFolder, fileName);
						
		}
		
		return libMap.get(nativeName).toString();
	}
	
	public static void loadNative(String nativeName) {
		String filePath = getNative(nativeName);
		if (filePath == null) {
			throw new UnsatisfiedLinkError("NativeLoader: Could not load the native " + nativeName + "!");
		} else {
			System.load(filePath);
		}
	}
	
}
