package de.m_marvin.metabuild.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;

import de.m_marvin.metabuild.core.Metabuild;

public class FileUtility {
	
	public static List<File> deepList(File dir) {
		return deepList(dir, File::isFile);
	}
	
	public static List<File> deepList(File dir, Predicate<File> pred) {
		List<File> files = new ArrayList<>();
		Queue<File> scan = new ArrayDeque<>();
		scan.addAll(Arrays.asList(dir.listFiles()));
		while (!scan.isEmpty()) {
			File f = scan.poll();
			if (pred.test(f)) files.add(f);
			scan.addAll(Arrays.asList(f.listFiles()));
		}
		return files;
	}
	
	public static File absolute(File path) {
		return absolute(path, Metabuild.get().workingDir());
	}

	public static File absolute(File path, File base) {
		return Paths.get(base.getPath()).resolve(path.getPath()).toFile();
	}
	
	public static File relative(File path) {
		return relative(path, Metabuild.get().buildDir());
	}
	
	public static File relative(File path, File base) {
		return Paths.get(base.getPath()).relativize(Paths.get(path.getPath())).toFile();
	}
	
	public static File concat(File... paths) {
		if (paths.length == 0)
			return null;
		File path = paths[0];
		for (int i = 1; i < paths.length; i++)
			path = new File(path, paths[i].getPath());
		return path;
	}
	
	public static Optional<FileTime> timestamp(File file) {
		try {
			if (!file.isFile()) return Optional.empty();
			BasicFileAttributes atr = Files.readAttributes(Paths.get(file.getPath()), BasicFileAttributes.class);
			return Optional.of(atr.lastModifiedTime());
		} catch (IOException e) {
			return Optional.empty();
		}
	}
	
	public static void touch(File file) {
		touch(file, FileTime.fromMillis(System.currentTimeMillis()));
	}
	
	public static void touch(File file, FileTime timestamp) {
		if (file.isFile()) {
			try {
				Files.setLastModifiedTime(Paths.get(file.getPath()), timestamp);
			} catch (IOException e) {
				Metabuild.get().logger().warnt("FileUtility", "failed to update file timestamp: %s", file.getPath());;
			}
		}
	}
	
}
