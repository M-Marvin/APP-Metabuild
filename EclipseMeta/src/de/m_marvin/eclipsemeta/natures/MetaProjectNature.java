package de.m_marvin.eclipsemeta.natures;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

import de.m_marvin.eclipsemeta.MetaManager;
import de.m_marvin.metabuild.api.core.IMeta;
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
		meta.setWorkingDirectory(this.project.getLocation().toFile());
		// TODO setup console output
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
					MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
							"Meta claim Error", "Could not claim metabuld instance, Meta-Job could not be executed!");
					return;
				}
				monitor.clearBlocked();
				
				monitor.worked(1);
				monitor.subTask("Initializing Project Buildfile ...");
				
				if (!setupBuildsystem()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
							"Meta Build", "Failed to initialize build file!");
				}

				monitor.worked(1);
				monitor.subTask("Loading Meta Tasks ...");
				
				this.tasks.clear();
				this.groups.clear();
				this.meta.getTasks(this, this.groups, this.tasks);
				MetaProjectNature.this.state = MetaState.LOADED;
				
				monitor.worked(1);
			} catch (Throwable e) {
				MetaProjectNature.this.state = MetaState.ERROR;
				ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
						"Meta Build", "Error while trying to execute meta process!", 
						MultiStatus.error(e.getLocalizedMessage(), e));
			}
			
			freeMeta();
			
		}).schedule();
		
	}
	
	public void runTask(String task) {
		
		Job.create("Meta", monitor -> {
			
			// TODO task execution not implemented
			
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
