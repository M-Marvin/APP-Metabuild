package de.m_marvin.metabuild.core.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class ZipTask extends BuildTask {

	public final Map<File, String> entries = new HashMap<>();
	public final Set<File> includes = new HashSet<>();
	public File archive = new File("out.zip");
	public Predicate<File> entryPredicate = f -> true;
	public Predicate<File> includePredicate = f -> true;
	
	protected Map<File, String> toArchive;
	protected Set<File> toInclude;
	
	public ZipTask(String name) {
		super(name);
		this.type = TaskType.named("MAKE_ARCHIVE");
	}
	
	protected boolean archiveFile(ZipOutputStream zstream, String eloc, File file) throws IOException {
		try {

			status("archiving > " + file.getPath());
			
			long size = file.length();
			
			ZipEntry zipEntry = new ZipEntry(eloc);
			zipEntry.setSize(size);
			zipEntry.setTime(System.currentTimeMillis());
			
			zstream.putNextEntry(zipEntry);
			InputStream fstream = new FileInputStream(file);
			byte[] buffer = new byte[2048];
			while (size > 0) {
				int len = fstream.read(buffer);
				zstream.write(buffer, 0, len);
				size -= len;
			}
			fstream.close();
			zstream.closeEntry();
			return true;
		} catch (FileNotFoundException e) {
			logger().errort(logTag(), "could not open file: %s", file);
			return false;
		}
	}
	
	protected boolean archiveInclude(ZipOutputStream zstream, ZipInputStream archive, ZipEntry entry) throws IOException {
		try {

			status("archiving > " + entry.getName());
			
			zstream.putNextEntry(new ZipEntry(entry.getName()));
			byte[] buffer = new byte[2048];
			int len;
			while ((len = archive.read(buffer)) > 0)
				zstream.write(buffer, 0, len);
			zstream.closeEntry();
			archive.closeEntry();
		} catch (ZipException e) {
			if (e.getMessage().startsWith("duplicate entry")) {
				zstream.closeEntry();
				logger().debugt(logTag(), "ignore duplicate entry: %s", entry.getName());
			} else {
				throw e;
			}
		}
		archive.closeEntry();
		return true;
	}
	
	protected boolean archiveFiles(ZipOutputStream zstream, Map<File, String> files) {
		for (var entry : files.entrySet()) {
			try {
				status("archive > " + entry.getValue());
				logger().debugt(logTag(), "archive file: %s - %s", entry.getValue(), entry.getKey());
				if (!archiveFile(zstream, entry.getValue(), entry.getKey())) {
					logger().errort(logTag(), "failed to archive file: %s", entry.getValue());
					return false;
				}
			} catch (IOException e) {
				throw BuildException.msg(e, "failed to archive file: %s", entry.getValue());
			}
		}
		return true;
	}
	
	protected boolean archiveIncludes(ZipOutputStream zstream, Set<File> includes) {
		for (File includeFile : includes) {
			if (FileUtility.isArchive(includeFile)) {
				try {
					ZipInputStream archive = new ZipInputStream(new FileInputStream(includeFile));
					ZipEntry include;
					while ((include = archive.getNextEntry()) != null) {
						if (include.isDirectory()) continue;
						status("include > " + include.getName() + " - " + includeFile);
						logger().debugt(logTag(), "archive included entries: %s - %s", include.getName(), includeFile);
						if (!archiveInclude(zstream, archive, include)) {
							logger().errort(logTag(), "failed to include archive file entry: %s - %s", include.getName(), includeFile);
							return false;
						}
					}
					archive.close();
				} catch (IOException e) {
					throw BuildException.msg(e, "failed to include archive: %s", includeFile);
				}
			} else {
				try {
					for (File archiveFile : FileUtility.deepList(includeFile, f -> f.isFile() && this.includePredicate.test(f))) {
						File loc = FileUtility.relative(archiveFile.getParentFile(), includeFile);
						status("include > " + loc + " - " + archiveFile);
						if (!archiveFile(zstream, loc.toString(), archiveFile)) {
							logger().errort(logTag(), "failed to include file entry: %s - %s", loc, archiveFile);
							return false;
						}
					}
				} catch (IOException e) {
					throw BuildException.msg(e, "failed to include file or directory: %s", includeFile);
				}
			}
			
		}
		return true;
	}

	protected boolean archiveAdditional(ZipOutputStream zstream) { return true; };
	
	@Override
	public TaskState prepare() {
		
		if (this.toArchive != null)
			return this.toArchive.isEmpty() ? TaskState.UPTODATE : TaskState.OUTDATED;
		
		File archiveFile = FileUtility.absolute(this.archive);
		Optional<FileTime> lasttime = FileUtility.timestamp(archiveFile);
		Optional<FileTime> timestamp = Optional.empty();
		
		// Get files to archive and determine timestamp
		this.toArchive = new HashMap<>();
		for (var entry : this.entries.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null)
				throw BuildScriptException.msg("no null entries allowed in ZipTask: %s - %s", entry.getKey(), entry.getValue());
			File eloc = new File(entry.getValue());
			File oloc = FileUtility.absolute(entry.getKey());
			for (File file : FileUtility.deepList(oloc, f -> f.isFile() && this.entryPredicate.test(f))) {
				
				Optional<FileTime> filetime = FileUtility.timestamp(file);
				if (timestamp.isEmpty() || filetime.isEmpty() || timestamp.get().compareTo(filetime.get()) < 0)
					timestamp = filetime;
				
				File floc = FileUtility.concat(eloc, FileUtility.relative(file, oloc));
				String ename = floc.getPath().replace('\\', '/');
				if (ename.startsWith("/")) ename = ename.substring(1);
				this.toArchive.put(file, ename);
			}
		}
		
		// Get archives and directories to include and determine timestamp
		this.toInclude = new HashSet<>();
		if (this.includes.contains(null))
			throw BuildScriptException.msg("no null entries allowed in ZipTask includes");
		for (File file : FileUtility.parseFilePaths(this.includes)) {
			for (File file1 : FileUtility.deepList(file, f -> f.isFile() && this.includePredicate.test(f))) {
				Optional<FileTime> filetime = FileUtility.timestamp(file1);
				if (timestamp.isEmpty() || filetime.isEmpty() || timestamp.get().compareTo(filetime.get()) < 0)
					timestamp = filetime;
			}
			this.toInclude.add(file);
		}
		
		boolean outdatedFiles = lasttime.isEmpty() || !timestamp.isEmpty() && timestamp.get().compareTo(lasttime.get()) > 0;
		return outdatedFiles ? TaskState.OUTDATED : TaskState.UPTODATE;
	}
	
	@Override
	public boolean run() {
		
		logger().infot(logTag(), "make archive from files: %s", this.archive);
		
		File archiveFile = FileUtility.absolute(this.archive);
		try {
			if (!archiveFile.getParentFile().isDirectory() && !archiveFile.getParentFile().mkdirs()) {
				logger().errort(logTag(), "failed to create output directory for archive: %s", archiveFile.getParentFile());
				return false;
			}
			
			ZipOutputStream zstream = new ZipOutputStream(new FileOutputStream(archiveFile));
			if (!archiveAdditional(zstream)) return false;
			if (!archiveFiles(zstream, this.toArchive)) return false;
			if (!archiveIncludes(zstream, this.toInclude)) return false;
			zstream.finish();
			zstream.flush();
			zstream.close();
			
			return true;
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to create archive file: %s", this.archive);
		}
		
	}
	
	@Override
	protected void cleanup() {
		this.toArchive = null;
	}
	
}
