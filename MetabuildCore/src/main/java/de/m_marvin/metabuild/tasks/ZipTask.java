package de.m_marvin.metabuild.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class ZipTask extends BuildTask {

	public final Map<File, String> entries = new HashMap<>();
	public File archive = new File("out.zip");
	public Predicate<File> filePredicate = f -> true;
	
	protected Map<File, String> toArchive;
	
	public ZipTask(String name) {
		super(name);
		this.type = TaskType.named("MAKE_ARCHIVE");
	}
	
	protected boolean archiveFile(ZipOutputStream zstream, String eloc, File file) throws IOException {
		try {
			InputStream fstream = new FileInputStream(file);
			byte[] data = fstream.readAllBytes();
			fstream.close();
			
			ZipEntry zipEntry = new ZipEntry(eloc);
			zipEntry.setSize(data.length);
			zipEntry.setTime(System.currentTimeMillis());
			
			zstream.putNextEntry(zipEntry);
			zstream.write(data);
			zstream.closeEntry();
			return true;
		} catch (FileNotFoundException e) {
			logger().errort(logTag(), "could not open file: %s", file);
			return false;
		}
	}
	
	protected boolean archiveFiles(ZipOutputStream zstream) {
		for (var entry : this.toArchive.entrySet()) {
			try {
				logger().debugt(logTag(), "archive file: %s", entry.getValue());
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
	
	@Override
	public TaskState prepare() {
		
		File archiveFile = FileUtility.absolute(this.archive);
		Optional<FileTime> lasttime = FileUtility.timestamp(archiveFile);
		Optional<FileTime> timestamp = Optional.empty();
		
		// Get files to archive and determine timestamp
		this.toArchive = new HashMap<>();
		for (var entry : this.entries.entrySet()) {
			File eloc = new File(entry.getValue());
			for (File file : FileUtility.deepList(entry.getKey(), f -> f.isFile() && this.filePredicate.test(f))) {
				
				Optional<FileTime> filetime = FileUtility.timestamp(file);
				if (timestamp.isEmpty() || filetime.isEmpty() || timestamp.get().compareTo(filetime.get()) < 0)
					timestamp = filetime;
				
				File floc = FileUtility.concat(eloc, FileUtility.relative(file, entry.getKey()));
				this.toArchive.put(file, floc.getPath().substring(1).replace('\\', '/'));
			}
		}
		
		return (timestamp.isEmpty() || lasttime.isEmpty() || timestamp.get().compareTo(lasttime.get()) > 0) ? TaskState.OUTDATED : TaskState.UPTODATE;
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
			if (!archiveFiles(zstream)) return false;
			zstream.finish();
			zstream.flush();
			zstream.close();
			
			return true;
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to create archive file: %s", this.archive);
		}
		
	}
	
}
