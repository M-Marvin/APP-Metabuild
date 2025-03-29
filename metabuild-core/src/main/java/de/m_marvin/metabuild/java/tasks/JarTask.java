package de.m_marvin.metabuild.java.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.tasks.ZipTask;
import de.m_marvin.metabuild.core.util.FileUtility;

public class JarTask extends ZipTask {

	public static final String MANIFEST_LOC = "META-INF/MANIFEST.MF";
	public static final Pattern META_ENTRY_PATTERN = Pattern.compile("[\\w\\-\\d]+");
	
	public final Map<String, String> metainfo = new HashMap<>();
	public final Set<File> classpathIncludes = new HashSet<>();
	public Predicate<File> classpathPredicate = f -> {
		if (f.isFile() && !FileUtility.getExtension(f).equals("jar")) return false;
		String name = FileUtility.getNameNoExtension(f);
		return !name.endsWith("-sources") && !name.endsWith("-javadoc");
	};
	
	public JarTask(String name) {
		super(name);
		this.metainfo.put("Manifest-Version", "1.0");
	}
	
	protected boolean buildManifest(StringBuffer sbuffer) {
		for (var entry : this.metainfo.entrySet()) {
			if (!META_ENTRY_PATTERN.matcher(entry.getKey()).find()) {
				logger().errort(logTag(), "invalid manifest entry key: %s", entry.getKey());
				return false;
			}
			sbuffer.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
		}
		return true;
	}
	
	protected boolean archiveManifest(ZipOutputStream zstream) throws IOException {
		StringBuffer sbuffer = new StringBuffer();
		if (!buildManifest(sbuffer)) {
			logger().errort(logTag(), "could not write manifest!");
			return false;
		}
		zstream.putNextEntry(new ZipEntry(MANIFEST_LOC));
		zstream.write(sbuffer.toString().getBytes(StandardCharsets.UTF_8));
		return true;
	}
	
	@Override
	protected boolean archiveFiles(ZipOutputStream zstream, Map<File, String> files) {
		try {
			logger().debugt(logTag(), "archive manifest");
			if (!archiveManifest(zstream)) {
				logger().errort(logTag(), "failed to archive manifest!");
				return false;
			}
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to archive manifest!");
		}
		return super.archiveFiles(zstream, files);
	}
	
	@Override
	protected boolean archiveIncludes(ZipOutputStream zstream, Set<File> archives) {
		try {
			for (File classpathFile : this.classpathIncludes) {
				logger().debugt(logTag(), "include classpath archives: %s", classpathFile);
				classpathFile = FileUtility.absolute(classpathFile);
				String classpath = FileUtility.readFileUTF(classpathFile);
				if (classpath == null) {
					logger().debugt(logTag(), "classpath file not found: %s", classpathFile);
					continue;
				}
				List<File> entries = Stream.of(classpath.split(";")).map(File::new).toList();
				for (File entry : entries) {
					if (!this.classpathPredicate.test(entry)) continue;
					if (entry.isDirectory()) {
						Map<File, String> toArchive = FileUtility.deepList(entry).stream()
								.collect(Collectors.toMap(f -> f, f -> FileUtility.relative(f, entry).getPath()));
						if (!super.archiveFiles(zstream, toArchive)) return false;
					} else if (FileUtility.isArchive(entry)) {
						if (!super.archiveIncludes(zstream, Collections.singleton(entry))) return false;
					} else if (entry.isFile()) {
						if (!super.archiveFile(zstream, "", entry)) return false;
					}
				}
			}
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to archive classpath entries!");
		}
		return super.archiveIncludes(zstream, archives);
	}
	
}
