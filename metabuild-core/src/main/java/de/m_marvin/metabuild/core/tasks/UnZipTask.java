 package de.m_marvin.metabuild.core.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class UnZipTask extends BuildTask {

	public final List<File> archives = new ArrayList<File>();
	public File output = new File("out");
	public Predicate<File> extractPredicate = z -> true;
	public boolean passtroughNonArchive = true;
	public boolean passtroughFolders = false;
	public boolean deleteUnexpectedFiles = true;
	
	protected List<File> toExtract = null;
	protected List<File> filesExpected = null;
	
	public UnZipTask(String name) {
		super(name);
		this.type = TaskType.named("EXTRACT_ARCHIVE");
	}
	
	protected Collection<File> getOutputs(File archiveFile) {
		try {
			ZipFile zip = new ZipFile(archiveFile);
			List<File> files = zip.stream()
				.map(z -> new File(this.output, z.getName()))
				.filter(this.extractPredicate)
				.toList();
			zip.close();
			return files;
		} catch (Exception e) {
			if (archiveFile.isDirectory() && this.passtroughFolders) {
				return FileUtility.deepList(archiveFile).stream().map(f -> FileUtility.concat(this.output, FileUtility.relative(f, archiveFile))).toList();
			} else if (archiveFile.isFile()) {
				File outputFile = new File(this.output, archiveFile.getName());
				if (this.passtroughNonArchive && this.extractPredicate.test(outputFile))
					return FileUtility.deepList(outputFile);
				return Collections.emptyList();
			}
			return Collections.emptyList();
		}
	}
	
	@Override
	protected TaskState prepare() {
		
		this.toExtract = new ArrayList<File>();
		this.filesExpected = new ArrayList<File>();
		
		Collection<File> archiveFiles = FileUtility.parseFilePaths(this.archives);
		if (archiveFiles.isEmpty()) return TaskState.UPTODATE;
		
		for (File archiveFile : archiveFiles) {
			
			if (!FileUtility.isArchive(archiveFile) && !this.passtroughNonArchive) continue;
			if (archiveFile.isDirectory() && !this.passtroughFolders) continue;
			Optional<FileTime> timestamp = archiveFile.isFile() ? FileUtility.timestamp(archiveFile) : FileUtility.timestampDir(archiveFile);
			
			boolean missing = false;
			FileTime outputTimetstamp = null;
			for (File file : getOutputs(archiveFile)) {
				File outFile = FileUtility.absolute(file);
				filesExpected.add(outFile);
				Optional<FileTime> outTime = FileUtility.timestamp(outFile);
				if (outTime.isEmpty()) {
					missing = true;
					continue;
				}
				if (outputTimetstamp == null || outputTimetstamp.compareTo(outTime.get()) > 0)
					outputTimetstamp = outTime.get();
			}
			
			boolean outdated = missing || timestamp.isEmpty() || (outputTimetstamp != null && outputTimetstamp.compareTo(timestamp.get()) < 0);
			if (outdated) this.toExtract.add(archiveFile);
			
		}
		
		return this.toExtract.isEmpty() ? TaskState.UPTODATE : TaskState.OUTDATED;
		
	}
	
	@Override
	protected boolean run() {
		
		File outputFolder = FileUtility.absolute(this.output);
		if (!outputFolder.isDirectory() && !outputFolder.mkdirs())
			throw BuildException.msg("unable to create archive output folder: %s", outputFolder);
		
		// Delete files which are not part of the files extracted from the archives
		if (this.deleteUnexpectedFiles) {
			for (File file : FileUtility.deepList(outputFolder, f -> f.isFile() && !this.filesExpected.contains(f))) {
				logger().debugt(logTag(), "delete unexpected file from archive output: %s", file);
				if (!FileUtility.delete(file)) {
					logger().errort(logTag(), "unable to delete file!");
					return false;
				}
			}
		}
		
		// Extract archives to output folder
		for (File archiveFile : this.toExtract) {
			
			if (!FileUtility.isArchive(archiveFile)) {

				if (archiveFile.isDirectory() && this.passtroughFolders) {

					logger().debugt(logTag(), "copy non archive files: %s -> %s", archiveFile, this.output);
					
					for (File file : FileUtility.deepList(archiveFile)) {
						File f = FileUtility.concat(outputFolder, FileUtility.relative(file, archiveFile));
						if (this.extractPredicate.test(f)) {
							if (!f.getParentFile().isDirectory()) f.getParentFile().mkdirs();
							if (!FileUtility.copy(file, f.getParentFile())) return false;
						}
					}
					
				} else if (archiveFile.isFile()) {

					if (this.extractPredicate.test(new File(outputFolder, archiveFile.getName()))) {

						logger().debugt(logTag(), "copy non archive file: %s -> %s", archiveFile, this.output);
						if (!FileUtility.copy(archiveFile, outputFolder)) return false;
						
					}
					
				}
				
			} else {

				logger().debugt(logTag(), "unpack archive files: %s -> %s", archiveFile, this.output);
				
				try {
					ZipInputStream zstream = new ZipInputStream(new FileInputStream(archiveFile));
					ZipEntry entry;
					while ((entry = zstream.getNextEntry()) != null) {
						try {
							File outputFile = new File(outputFolder, entry.getName());
							if (this.extractPredicate.test(outputFile)) {
								logger().debugt(logTag(), "extracting entry: %s", entry.getName());
								OutputStream fstream = new FileOutputStream(outputFile);
								zstream.transferTo(fstream);
								fstream.close();
							}
							zstream.closeEntry();
						} catch (IOException e) {
							zstream.close();
							throw BuildException.msg(e, "unable to extract archive entry: %s", entry.getName());
						}
					}
					zstream.close();
				} catch (IOException e) {
					throw BuildException.msg(e, "unable to extract archive: %s", archiveFile);
				}
				
			}
			
		}
		
		return true;
		
	}
	
	@Override
	protected void cleanup() {
		this.toExtract = null;
		this.filesExpected = null;
	}
	
}
