package de.m_marvin.metabuild.core.tasks;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.script.TaskType;
import de.m_marvin.metabuild.core.util.FileUtility;

public class FileTask extends BuildTask {
	
	public static enum Action {
		MOVE,
		COPY,
		DELETE;
	}
	
	public Action action;
	public File target;
	public File destination;
	public boolean renameFile;

	public FileTask(String name, Action action, File from, File to, boolean rename) {
		super(name);
		this.action = action;
		this.target = from;
		this.destination = to;
		this.renameFile = rename;
	}
	
	public FileTask(String name, Action action, File from, File to) {
		super(name);
		this.action = action;
		this.target = from;
		this.destination = to;
		this.renameFile = false;
	}
	
	public FileTask(String name, Action action, File target) {
		super(name);
		this.action = action;
		this.target = target;
		this.destination = null;
		this.renameFile = false;
	}
	
	public FileTask(String name) {
		super(name);
		this.type = TaskType.named("files");
	}
	
	@Override
	protected TaskState prepare() {
		
		if (this.action == null || this.target == null) {
			throw BuildException.msg("file task not configured!");
		} else if (this.action != Action.DELETE && this.destination == null) {
			throw BuildException.msg("file task not configured!");
		}
		
		File targetFile = FileUtility.absolute(this.target);
		
		if (this.action == Action.COPY) {
			File destFile = this.renameFile ? FileUtility.absolute(this.destination) : new File(FileUtility.absolute(this.destination), targetFile.getName());
			long toReplace = FileUtility.deepList(targetFile).stream()
					.filter(file -> {
						File df = FileUtility.absolute(FileUtility.relative(file, targetFile), destFile);
						Optional<FileTime> lasttime = FileUtility.timestamp(df);
						Optional<FileTime> timestamp = FileUtility.timestamp(file);
						return lasttime.isEmpty() || timestamp.isEmpty() || lasttime.get().compareTo(timestamp.get()) < 0;
					})
					.count();
			return toReplace == 0 ? TaskState.UPTODATE : TaskState.OUTDATED;
		} else {
			return !targetFile.exists() ? TaskState.UPTODATE : TaskState.OUTDATED;
		}
		
	}
	
	@Override
	protected boolean run() {
		
		switch (this.action) {
		case DELETE:
			logger().infot(logTag(), "delete files: %s", this.target);
			if (!FileUtility.delete(FileUtility.absolute(this.target))) {
				logger().errort(logTag(), "failed to delete all files!");
				return false;
			}
			return true;
		case MOVE:
			logger().infot(logTag(), "move files: %s -> %s", this.target, this.destination);
			if (!this.destination.getParentFile().isDirectory())
				this.destination.getParentFile().mkdirs();
			if (!FileUtility.move(FileUtility.absolute(this.target), FileUtility.absolute(this.destination), this.renameFile)) {
				logger().errort(logTag(), "failed to move all files!");
				return false;
			}
			return true;
		case COPY:
			logger().infot(logTag(), "copy files: %s -> %s", this.target, this.destination);
			if (!this.destination.getParentFile().isDirectory())
				this.destination.getParentFile().mkdirs();
			if (!FileUtility.copy(FileUtility.absolute(this.target), FileUtility.absolute(this.destination), this.renameFile)) {
				logger().errort(logTag(), "failed to copy all files!");
				return false;
			}
			return true;
		default:
			return false;
		}
		
	}

}
