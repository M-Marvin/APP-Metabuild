package de.m_marvin.metabuild.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;

import de.m_marvin.metabuild.core.Metabuild;

public class FileUtility {
	
	public static List<File> deepList(File dir) {
		return deepList(dir, File::isFile);
	}
	
	public static List<File> deepList(File path, Predicate<File> pred) {
		Objects.requireNonNull(path);
		Objects.requireNonNull(pred);
		List<File> files = new ArrayList<>();
		if (pred.test(path)) files.add(path);
		if (path.isDirectory()) {
			Queue<File> scan = new ArrayDeque<>();
			scan.addAll(Arrays.asList(path.listFiles()));
			while (!scan.isEmpty()) {
				File f = scan.poll();
				if (pred.test(f)) files.add(f);
				if (f.isDirectory()) scan.addAll(Arrays.asList(f.listFiles()));
			}
		}
		return files;
	}
	
	public static File absolute(File path) {
		Objects.requireNonNull(path);
		return absolute(path, Metabuild.get().workingDir());
	}

	public static File absolute(File path, File base) {
		Objects.requireNonNull(path);
		Objects.requireNonNull(base);
		return Paths.get(base.getPath()).resolve(path.getPath()).toFile();
	}
	
	public static File relative(File path) {
		Objects.requireNonNull(path);
		return relative(path, Metabuild.get().workingDir());
	}
	
	public static File relative(File path, File base) {
		Objects.requireNonNull(path);
		Objects.requireNonNull(base);
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
		Objects.requireNonNull(file);
		try {
			if (!file.isFile()) return Optional.empty();
			BasicFileAttributes atr = Files.readAttributes(Paths.get(file.getPath()), BasicFileAttributes.class);
			return Optional.of(FileTime.fromMillis(atr.lastModifiedTime().toMillis()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}
	
	public static Optional<FileTime> timestampDir(File directory) {
		if (directory.isFile()) return timestamp(directory);
		var latest = deepList(directory).stream().map(FileUtility::timestamp).reduce(FileUtility::latest);
		if (latest.isEmpty()) return Optional.empty();
		return latest.get();
	}
	
	@SafeVarargs
	public static Optional<FileTime> latest(Optional<FileTime>... timestamps) {
		Optional<FileTime> latest = Optional.empty();
		for (Optional<FileTime> t : timestamps) {
			if (latest.isEmpty()) {
				latest = t;
				continue;
			}
			if (latest.isPresent() && latest.get().compareTo(t.get()) < 0) latest = t;
		}
		return latest;
	}

	@SafeVarargs
	public static Optional<FileTime> latest(FileTime... timestamps) {
		Optional<FileTime> latest = Optional.empty();
		for (FileTime t : timestamps) {
			if (latest.isEmpty()) {
				latest = Optional.of(t);
				continue;
			}
			if (latest.isPresent() && latest.get().compareTo(t) < 0) latest = Optional.of(t);
		}
		return latest;
	}
	
	public static void touch(File file) {
		Objects.requireNonNull(file);
		touch(file, FileTime.fromMillis(System.currentTimeMillis()));
	}
	
	public static void touch(File file, FileTime timestamp) {
		Objects.requireNonNull(file);
		Objects.requireNonNull(timestamp);
		if (file.isFile()) {
			try {
				Files.setLastModifiedTime(Paths.get(file.getPath()), timestamp);
			} catch (IOException e) {
				Metabuild.get().logger().warnt("FileUtility", "failed to update file timestamp: %s", file.getPath());;
			}
		}
	}
	
	public static String getExtension(File path) {
		Objects.requireNonNull(path);
		if (path.isDirectory()) return "";
		String name = path.getName();
		int i = name.lastIndexOf('.');
		if (i == -1) return "";
		return name.substring(i + 1);
	}
	
	
	public static String getNameNoExtension(File path) {
		Objects.requireNonNull(path);
		if (path.isDirectory()) return path.getName();
		String name = path.getName();
		int i = name.lastIndexOf('.');
		if (i == -1) return name;
		return name.substring(0, i);
	}
	
	public static File changeExtension(File file, String ext) {
		Objects.requireNonNull(file);
		Objects.requireNonNull(ext);
		File path = file.getParentFile();
		String name = getNameNoExtension(file);
		return new File(path, name + (ext.isBlank() ? "" : "." + ext));
	}

	protected static boolean relocate(File from, File to, boolean rename, boolean copy) {
		File t = rename ? to : new File(to, from.getName());
		if (from.isDirectory()) {
			if (!t.exists() && !t.mkdir()) return false;
			for (File f : from.listFiles()) {
				relocate(f, t, false, copy);
			}
			if (copy) return true;
			try {
				Files.delete(from.toPath());
				return true;
			} catch (IOException e) {
				return false;
			}
		} else if (from.isFile()) {
			try {
				if (copy)
					Files.copy(from.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
				else
					Files.move(from.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
				return true;
			} catch (IOException e) {
				return false;
			}
		} else {
			return false;
		}
	}
	
	public static boolean delete(File file) {
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				if (!delete(f)) return false;
			}
		}
		return file.delete();
	}
	
	public static boolean move(File from, File to) {
		if (!to.exists()) {
			return relocate(from, to, true, false);
		} else if (from.isFile() && to.isFile()) {	
			return relocate(from, to, true, false);
		} else if (to.isDirectory()) {
			return relocate(from, to, false, false);
		} else {
			return false;
		}	
	}

	public static boolean copy(File from, File to) {
		if (!to.exists()) {
			return relocate(from, to, true, true);
		} else if (from.isFile() && to.isFile()) {	
			return relocate(from, to, true, true);
		} else if (to.isDirectory()) {
			return relocate(from, to, false, true);
		} else {
			return false;
		}	
	}
	
	public static Optional<File> locateOnPath(String nameOrPath) {
		File f = new File(nameOrPath);
		if (f.isFile()) return Optional.of(f);
		f = absolute(f);
		if (f.isFile()) return Optional.of(f);
		String systemPath = System.getenv("PATH");
		if (systemPath == null) systemPath = System.getenv("path");
		String[] pathDirs = systemPath.split(File.pathSeparator);
		for (String pathDir : pathDirs) {
			File pathDirectory = new File(pathDir);
			for (File file : pathDirectory.listFiles()) {
				if (file.getName().equals(nameOrPath) || getNameNoExtension(file).equals(nameOrPath)) return Optional.of(file);
			}
		}
		return Optional.empty();
	}
	
}
