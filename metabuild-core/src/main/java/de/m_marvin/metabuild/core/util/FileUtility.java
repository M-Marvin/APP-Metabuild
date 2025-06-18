package de.m_marvin.metabuild.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.XMLStream.DescType;
import de.m_marvin.basicxml.XMLStream.ElementDescriptor;
import de.m_marvin.metabuild.core.Metabuild;

public class FileUtility {

	private FileUtility() {}

	public static List<File> deepList(Collection<File> dirs) {
		return dirs.stream().flatMap(f -> deepList(f).stream()).toList();
	}
	
	public static List<File> deepList(File dir) {
		return deepList(dir, File::isFile);
	}

	public static List<File> deepList(Collection<File> dirs, Predicate<File> pred) {
		return dirs.stream().flatMap(f -> deepList(f, pred).stream()).toList();
	}
	
	public static List<File> deepList(File path, Predicate<File> pred) {
		Objects.requireNonNull(path, "path can not be null");
		Objects.requireNonNull(pred, "pred can not be null");
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
		Objects.requireNonNull(path, "path can not be null");
		return absolute(path, Metabuild.get().buildWorkingDir());
	}

	public static File absolute(File path, File base) {
		Objects.requireNonNull(path, "path can not be null");
		Objects.requireNonNull(base, "base can not be null");
		return Paths.get(base.getPath()).resolve(path.getPath()).normalize().toFile();
	}
	
	public static File relative(File path) {
		Objects.requireNonNull(path, "from can not be null");
		return relative(path, Metabuild.get().buildWorkingDir());
	}
	
	public static File relative(File path, File base) {
		Objects.requireNonNull(path, "path can not be null");
		Objects.requireNonNull(base, "base can not be null");
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
		Objects.requireNonNull(file, "file can not be null");
		try {
			if (!file.isFile()) return Optional.empty();
			BasicFileAttributes atr = Files.readAttributes(Paths.get(file.getPath()), BasicFileAttributes.class);
			return Optional.of(FileTime.fromMillis(atr.lastModifiedTime().toMillis()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}
	
	public static Optional<FileTime> timestampDir(File directory) {
		Objects.requireNonNull(directory, "directory can not be null");
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
		Objects.requireNonNull(file, "file can not be null");
		touch(file, FileTime.fromMillis(System.currentTimeMillis()));
	}
	
	public static void touch(File file, FileTime timestamp) {
		Objects.requireNonNull(file, "file can not be null");
		Objects.requireNonNull(timestamp, "timestamp can not be null");
		if (file.isFile()) {
			try {
				Files.setLastModifiedTime(Paths.get(file.getPath()), timestamp);
			} catch (IOException e) {
				Metabuild.get().logger().warnt("FileUtility", "failed to update file timestamp: %s", file.getPath());;
			}
		}
	}
	
	public static String getExtension(File path) {
		Objects.requireNonNull(path, "path can not be null");
		if (path.isDirectory()) return "";
		String name = path.getName();
		int i = name.lastIndexOf('.');
		if (i == -1) return "";
		return name.substring(i + 1);
	}
	
	
	public static String getNameNoExtension(File path) {
		Objects.requireNonNull(path, "path can not be null");
		if (path.isDirectory()) return path.getName();
		String name = path.getName();
		int i = name.lastIndexOf('.');
		if (i == -1) return name;
		return name.substring(0, i);
	}
	
	public static File changeExtension(File file, String ext) {
		Objects.requireNonNull(file, "file can not be null");
		Objects.requireNonNull(ext, "ext can not be null");
		File path = file.getParentFile();
		String name = getNameNoExtension(file);
		return new File(path, name + (ext.isBlank() ? "" : "." + ext));
	}

	protected static boolean relocate(File from, File to, boolean rename, boolean copy) {
		Objects.requireNonNull(from, "from can not be null");
		Objects.requireNonNull(to, "to can not be null");
		
		File t = rename ? to : new File(to, from.getName());
		if (from.isDirectory()) {
			if (!t.exists() && !t.mkdirs()) return false;
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
				touch(t);
				return true;
			} catch (IOException e) {
				return false;
			}
		} else {
			return false;
		}
	}
	
	public static boolean delete(File file) {
		Objects.requireNonNull(file, "file can not be null");
		
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				if (!delete(f)) return false;
			}
		}
		return file.delete();
	}	

	public static boolean move(File from, File to) {
		Objects.requireNonNull(from, "from can not be null");
		Objects.requireNonNull(to, "to can not be null");
		
		return move(from, to, false);
	}

	public static boolean move(File from, File to, boolean rename) {
		Objects.requireNonNull(from, "from can not be null");
		Objects.requireNonNull(to, "to can not be null");
		
		return relocate(from, to, rename, false);
	}

	public static boolean copy(File from, File to) {
		Objects.requireNonNull(from, "from can not be null");
		Objects.requireNonNull(to, "to can not be null");
		
		return copy(from, to, false);
	}
	
	public static boolean copy(File from, File to, boolean rename) {
		Objects.requireNonNull(from, "from can not be null");
		Objects.requireNonNull(to, "to can not be null");
		
		return relocate(from, to, rename, true);
	}
	
	public static Optional<File> locateOnPath(String nameOrPath, Predicate<File> predicate) {
		Objects.requireNonNull(nameOrPath, "nameOrPath can not be null");
		
		File f = new File(nameOrPath);
		if (f.isFile()) return Optional.of(f);
		f = absolute(f);
		if (f.isFile()) return Optional.of(f);
		String systemPath = System.getenv("PATH");
		if (systemPath == null) systemPath = System.getenv("path");
		String[] pathDirs = systemPath.split(File.pathSeparator);
		for (String pathDir : pathDirs) {
			File pathDirectory = new File(pathDir);
			if (!pathDirectory.exists()) continue;
			for (File file : pathDirectory.listFiles()) {
				if (file.getName().equals(nameOrPath) || getNameNoExtension(file).equals(nameOrPath) && predicate.test(file))
					return Optional.of(file);
			}
		}
		return Optional.empty();
	}
	
	public static Optional<File> locateOnPath(String nameOrPath) {
		// ignores shared libraries on linux and windows to only list (potentially) executables
		return locateOnPath(nameOrPath, file -> {
			String extension = getExtension(file);
			if (extension.equalsIgnoreCase("dll")) return false;
			if (extension.equalsIgnoreCase("sys")) return false;
			if (extension.equalsIgnoreCase("so")) return false;
			if (extension.equalsIgnoreCase("lib")) return false;
			return true;
		});
	}
	
	public static boolean isArchive(File file) {
		Objects.requireNonNull(file, "file can not be null");
		
		if (!file.isFile()) return false;
		try {
			new ZipFile(file).close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public static String readFileUTF(File file) {
		Objects.requireNonNull(file, "file can not be null");
		
		try {
			InputStream is = new FileInputStream(file);
			String utf = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			is.close();
			return utf;
		} catch (IOException e) {
			return null;
		}
	}
	
	public static boolean writeFileUTF(File file, String utf) {
		Objects.requireNonNull(file, "file can not be null");
		Objects.requireNonNull(utf, "utf can not be null");
		
		try {
			OutputStream os = new FileOutputStream(file);
			os.write(utf.getBytes(StandardCharsets.UTF_8));
			os.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public static boolean isIn(File path, File parent) {
		File f1 = absolute(path);
		File f2 = absolute(parent);
		return f1.toPath().startsWith(f2.toPath());
	}
	
	public static Collection<File> parseFilePaths(Collection<File> filepath) {
		Objects.requireNonNull(filepath, "filepath can not be null");
		
		return filepath.stream()
				.flatMap(entry -> {
					File fpath = absolute(entry);
					Collection<File> entries;
					if (fpath.isFile() && (entries = loadFilePath(fpath)) != null) {
						return entries.stream().map(FileUtility::absolute);
					}
					return Stream.of(fpath);
				})
				.distinct()
				.toList();
	}
	
	public static Collection<File> parseFilePaths(Collection<File> filepath, File base) {
		Objects.requireNonNull(filepath, "filepath can not be null");
		Objects.requireNonNull(base, "base can not be null");
		
		return filepath.stream()
				.flatMap(entry -> {
					File fpath = absolute(entry, base);
					Collection<File> entries;
					if (fpath.isFile() && (entries = loadFilePath(fpath)) != null) {
						return entries.stream().map(f -> absolute(f, base));
					}
					return Stream.of(fpath);
				})
				.distinct()
				.toList();
	}
	
	public static final URI METABUILD_FILEPATH_NAMESPACE = URI.create("https://github.com/M-Marvin/APP-Metabuild/filepath");
	
	public static Collection<File> loadFilePath(File filepath) {
		Objects.requireNonNull(filepath, "filepath can not be null");

		try {
			Set<String> entries = new HashSet<>();
			XMLInputStream xml = new XMLInputStream(new FileInputStream(filepath));
			// read first tag, check if filepath start tag
			ElementDescriptor filepathTag = xml.readNext();
			if (filepathTag == null) {
				xml.close();
				return null;
			}
			if (filepathTag.name().equals("filepath") && filepathTag.type() == DescType.OPEN && Objects.equals(filepathTag.namespace(), METABUILD_FILEPATH_NAMESPACE)) {
				ElementDescriptor pathTag;
				while ((pathTag = xml.readNext()) != null) {
					// if entry tag
					if (pathTag.name().equals("path") && pathTag.type() == DescType.OPEN) {
						String path = xml.readAllText();
						var pathTagClose = xml.readNext();
						if (!pathTagClose.isSameField(pathTag) && pathTagClose.type() != DescType.CLOSE) {
							xml.close();
							return null;
						}
						entries.add(path);
					// if filepath end tag
					} else if (pathTag.isSameField(filepathTag) && pathTag.type() == DescType.CLOSE) {
						xml.close();
						return entries.stream().map(s -> absolute(new File(s))).toList();
					}
				}
				// unexpected EOF
				xml.close();
				return null;
			}
			xml.close();
			return null;
		} catch (IOException | XMLException e) {
			return null;
		}
	}
	
	public static boolean writeFilePath(File filepathFile, Collection<File> entries) {
		Objects.requireNonNull(filepathFile, "filepathFile can not be null");
		Objects.requireNonNull(entries, "entries can not be null");
		
		try {
			XMLOutputStream xml = new XMLOutputStream(new FileOutputStream(filepathFile));
			xml.writeNext(new ElementDescriptor(DescType.OPEN, METABUILD_FILEPATH_NAMESPACE, "filepath", null));
			for (File entry : entries) {
				xml.writeNext(new ElementDescriptor(DescType.OPEN, METABUILD_FILEPATH_NAMESPACE, "path", null));
				xml.writeAllText(entry.toString(), false);
				xml.writeNext(new ElementDescriptor(DescType.CLOSE, METABUILD_FILEPATH_NAMESPACE, "path", null));
			}
			xml.writeNext(new ElementDescriptor(DescType.CLOSE, METABUILD_FILEPATH_NAMESPACE, "filepath", null));
			xml.close();
			return true;
		} catch (IOException | XMLException e) {
			return false;
		}
	}
	
}
