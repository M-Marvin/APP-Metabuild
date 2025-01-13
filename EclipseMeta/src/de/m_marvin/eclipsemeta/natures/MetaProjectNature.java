package de.m_marvin.eclipsemeta.natures;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.jobs.Job;

import de.m_marvin.eclipsemeta.MetaManager;
import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.IMeta.IStatusCallback;
import de.m_marvin.metabuild.api.core.MetaGroup;
import de.m_marvin.metabuild.api.core.MetaTask;

public class MetaProjectNature implements IProjectNature {

	public static final String NATURE_ID = "de.m_marvin.eclipsemeta.projectNature";
	
	public static enum MetaState {
		LOADED,NOT_LOADED,ERROR;
	}
	
	protected IProject project;
	protected IMeta meta;
	
	protected final List<MetaTask<MetaProjectNature>> tasks = new ArrayList<>();
	protected final List<MetaGroup<MetaProjectNature>> groups = new ArrayList<>();
	protected MetaState state = MetaState.NOT_LOADED;
	
	@Override
	public void configure() throws CoreException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deconfigure() throws CoreException {
		// TODO Auto-generated method stub
		
		this.state = MetaState.NOT_LOADED;
	}

	@Override
	public IProject getProject() {
		return this.project;
	}
	
	public MetaState getState() {
		return state;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}
	
	protected boolean claimMeta() {
		Optional<IMeta> meta = MetaManager.claimMeta();
		if (meta.isEmpty()) return false;
		this.meta = meta.get();
		return true;
	}
	
	protected void freeMeta() {
		if (this.meta == null) return;
		MetaManager.freeMeta(this.project, this.meta);
		this.meta = null;
	}
	
	protected boolean setupBuildsystem() {
		meta.setTerminalOutput((OutputStream) MetaUI.newConsoleStream());
		meta.setWorkingDirectory(this.project.getLocation().toFile());
		return meta.initBuild();
	}

	public void queryMetaTasks() {
		
		Job.create("Meta", monitor -> {
			
			monitor.beginTask("Meta Project reload", 3);
			
			try {
				
				monitor.subTask("Claiming Meta Instance ...");
				
				monitor.setBlocked(MultiStatus.info("Meta currently busy ..."));
				if (!claimMeta()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					monitor.setBlocked(MultiStatus.error("Failed to claim Meta instance"));
					MetaUI.openError("Meta claim Error", "Could not claim metabuld instance, Meta-Job could not be executed!");
					return;
				}
				monitor.clearBlocked();
				
				monitor.worked(1);
				if (monitor.isCanceled()) {
					freeMeta();
					return;
				}
				monitor.subTask("Initializing Project Buildfile ...");
				
				if (!setupBuildsystem()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					MetaUI.openError("Meta Build", "Failed to initialize build file!");
				}
				
				monitor.worked(1);
				if (monitor.isCanceled()) {
					freeMeta();
					return;
				}
				monitor.subTask("Loading Meta Tasks ...");
				
				this.tasks.clear();
				this.groups.clear();
				this.meta.getTasks(this, this.groups, this.tasks);
				MetaProjectNature.this.state = MetaState.LOADED;
				
				monitor.worked(1);
				
			} catch (Throwable e) {
				MetaProjectNature.this.state = MetaState.ERROR;
				MetaUI.openError(
					"Meta Build", "Error while trying to execute meta process!", 
					MultiStatus.error(e.getLocalizedMessage(), e));
			}
			
			freeMeta();

			MetaUI.refreshViewers();
			
		}).schedule();
		
	}
	
	public void runTask(String... tasks) {
		
		Job.create("Meta", monitor -> {

			try {
				
				monitor.subTask("Claiming Meta Instance ...");
				
				monitor.setBlocked(MultiStatus.info("Meta currently busy ..."));
				if (!claimMeta()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					monitor.setBlocked(MultiStatus.error("Failed to claim Meta instance"));
					MetaUI.openError("Meta claim Error", "Could not claim metabuld instance, Meta-Job could not be executed!");
					return;
				}
				monitor.clearBlocked();
				
				if (monitor.isCanceled()) {
					freeMeta();
					return;
				}
				monitor.subTask("Initializing Project Buildfile ...");
				
				if (!setupBuildsystem()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					MetaUI.openError("Meta Build", "Failed to initialize build file!");
				}
				
				this.meta.setStatusCallback(new IStatusCallback() {
					
					@Override
					public void taskStatus(String task, String status) {
						monitor.subTask(task + " > " + status);
					}
					
					@Override
					public void taskStarted(String task) {
						monitor.subTask(task + " > running");
					}
					
					@Override
					public void taskCount(int taskCount) {
						monitor.beginTask("Meta Build", taskCount);
					}
					
					@Override
					public void taskCompleted(String task) {
						monitor.worked(1);
					}
				});

				if (monitor.isCanceled()) {
					freeMeta();
					return;
				}
				monitor.subTask("Execute Tasks ...");
				
				this.meta.runTasks(tasks);
				MetaProjectNature.this.state = MetaState.LOADED;
				
			} catch (Throwable e) {
				MetaProjectNature.this.state = MetaState.ERROR;
				MetaUI.openError(
					"Meta Build", "Error while trying to execute meta process!", 
					MultiStatus.error(e.getLocalizedMessage(), e));
			}
			
			freeMeta();

			MetaUI.refreshViewers();
			
		}).schedule();
		
	}
	
	public void refreshProject() {
		queryMetaTasks();
		// TODO further refresh actions ?
	}
	
	public List<MetaTask<MetaProjectNature>> getMetaTasks() {
		return tasks;
	}

	public List<MetaGroup<MetaProjectNature>> getMetaGroups() {
		return groups;
	}
	
}
