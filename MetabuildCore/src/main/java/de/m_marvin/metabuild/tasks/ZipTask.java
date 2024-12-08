package de.m_marvin.metabuild.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.BuildTask;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class ZipTask extends BuildTask {

	public final Map<File, String> entries = new HashMap<>();
	public File archive = new File("out.zip");
	
	public ZipTask(String name) {
		super(name);
		this.type = TaskType.named("MAKE_ARCHIVE");
	}
	
	protected boolean archiveFile(ZipOutputStream zstream, File eloc, File file) throws IOException {
		try {
			InputStream fstream = new FileInputStream(file);
			byte[] data = fstream.readAllBytes();
			fstream.close();
			
			ZipEntry zipEntry = new ZipEntry(eloc.getPath().substring(1).replace('\\', '/'));
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
		for (var entry : this.entries.entrySet()) {
			File eloc = new File(entry.getValue());
			File fileDir = entry.getKey();
			
			for (File file : FileUtility.deepList(fileDir)) {
				File rfile = FileUtility.relative(file, fileDir);
				File feloc = FileUtility.concat(eloc, rfile);
				
				try {
					logger().debugt(logTag(), "archive file: %s", rfile);
					if (!archiveFile(zstream, feloc, file)) {
						logger().errort(logTag(), "failed to archive file: %s", file);
						return false;
					}
				} catch (IOException e) {
					throw BuildException.msg(e, "failed to archive file: %s", rfile);
				}
			}
		}
		return true;
	}
	
	@Override
	public TaskState prepare() {
		// TODO Auto-generated method stub
		return super.prepare();
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
