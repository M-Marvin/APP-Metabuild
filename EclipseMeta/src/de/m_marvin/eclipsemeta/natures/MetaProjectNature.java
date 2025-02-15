package de.m_marvin.eclipsemeta.natures;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import de.m_marvin.eclipsemeta.MetaManager;
import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.IMeta.IStatusCallback;
import de.m_marvin.metabuild.api.core.devenv.IJavaSourceIncludes;
import de.m_marvin.metabuild.api.core.tasks.MetaGroup;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

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
		Optional<IMeta> meta = MetaManager.claimMeta(this.project);
		if (meta.isEmpty()) return false;
		this.meta = meta.get();
		return true;
	}
	
	protected void freeMeta() {
		if (this.meta == null) return;
		MetaManager.freeMeta(this.project, this.meta);
		this.meta = null;
		System.gc(); // Attempt to free the meta jar files
	}
	
	protected boolean setupBuildsystem() {
		meta.setTerminalOutput(new PrintStream(MetaUI.newConsoleStream()), false);
		meta.setWorkingDirectory(this.project.getLocation().toFile());
		meta.setConsoleStreamInput(MetaUI.getConsoleInputStream());
		return meta.initBuild();
	}

	protected void runMeta(Consumer<IProgressMonitor> task) {

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

				this.meta.addStatusCallback(new IStatusCallback() {
					
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

					@Override
					public void buildCompleted(boolean success) {
						monitor.subTask("Build completed: " + (success ? "SUCCESS" : "FAILED"));
					}
					
				});
				
				if (!setupBuildsystem()) {
					MetaProjectNature.this.state = MetaState.ERROR;
					MetaUI.openError("Meta Build", "Failed to initialize build file!");
				}
				
				if (monitor.isCanceled()) {
					freeMeta();
					return;
				}
				
				task.accept(SubMonitor.convert(monitor));
				MetaProjectNature.this.state = MetaState.LOADED;
				
			} catch (Throwable e) {
				MetaProjectNature.this.state = MetaState.ERROR;
				MetaUI.openError(
					"Meta Build", "Error while trying to execute meta process!", 
					MultiStatus.error(e.getLocalizedMessage(), e));
				e.printStackTrace();
			}
			
			freeMeta();

			MetaUI.refreshViewers();
			
		}).schedule();
		
	}
	
	public void runTask(String... tasks) {
		
		runMeta(monitor -> {
			monitor.subTask("Excuting Tasks ...");
			this.meta.runTasks(tasks);
		});
		
	}
	
	public void refreshProject() {
		
		System.out.println("TEST");
		
		// TODO TESTING AREA
		
		
		
		if (true) return;
		
		runMeta(monitor -> {
			
			monitor.subTask("Loading Meta Tasks ...");
			
			this.tasks.clear();
			this.groups.clear();
			this.meta.getTasks(this, this.groups, this.tasks);
			MetaProjectNature.this.state = MetaState.LOADED;

			monitor.subTask("Run Dependency Tasks ...");
			
			List<String> dependTasks = this.groups.stream()
					.filter(g -> g.group().equals(IMeta.DEPENDENCY_TASK_GROUP))
					.flatMap(g -> this.tasks.stream()
							.filter(t -> t.group().isPresent() && t.group().get().equals(g)))
					.map(t -> t.name())
					.toList();
			
			if (dependTasks.isEmpty()) return;
			
			this.meta.setForceRunTasks(true);
			if (!this.meta.runTasks(dependTasks)) {
				MetaUI.openError("Update Meta Dependencies", "Failed to run dependency tasks!");
				return;
			}
			
			monitor.subTask("Load Dependencies ...");
			
			IJavaProject jp = JavaCore.create(this.project);
			
			if (jp != null) {

				try {

					List<IJavaSourceIncludes> javaIncludes = new ArrayList<>();
					this.meta.getSourceIncludes(javaIncludes, IJavaSourceIncludes.JAVA_LANGUAGE_ID);

					for (IJavaSourceIncludes js : javaIncludes) {
					
						List<IClasspathEntry> ncp = new ArrayList<>();
						Stream.of(jp.getRawClasspath())
								.filter(cpe -> cpe.getEntryKind() != IClasspathEntry.CPE_LIBRARY)
								.forEach(ncp::add);
						
						for (File binary : js.getSourceJars()) {
							// TODO javadoc attachments ?
							File sourceAttachment = js.getSourceAttachments().get(binary);
							IClasspathEntry cpe = JavaCore.newLibraryEntry(IPath.fromFile(binary), sourceAttachment == null ? null : IPath.fromFile(sourceAttachment), null);
							ncp.add(cpe);
						}
						
						jp.setRawClasspath(ncp.toArray(IClasspathEntry[]::new), new NullProgressMonitor());
						
					}
					
				} catch (JavaModelException e) {
					System.err.println("Failed to add source includes to classpath:");
					e.printStackTrace();
				}
				
			}
			
		});
		
	}
	
	public List<MetaTask<MetaProjectNature>> getMetaTasks() {
		return tasks;
	}

	public List<MetaGroup<MetaProjectNature>> getMetaGroups() {
		return groups;
	}
	
}
