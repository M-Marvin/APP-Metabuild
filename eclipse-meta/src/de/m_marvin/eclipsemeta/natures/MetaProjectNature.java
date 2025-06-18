package de.m_marvin.eclipsemeta.natures;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IPathEntry;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import de.m_marvin.eclipsemeta.MetaManager;
import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.api.core.IMeta.IStatusCallback;
import de.m_marvin.metabuild.api.core.devenv.ICppSourceIncludes;
import de.m_marvin.metabuild.api.core.devenv.IJavaSourceIncludes;
import de.m_marvin.metabuild.api.core.tasks.MetaGroup;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

public class MetaProjectNature implements IProjectNature {

	/**
	 * TODO EclipseMeta Features
	 * - Run-Configurations for Java projects
	 * - Run-Configurations for C projects
	 * - Creation Wizards (Meta C and Meta Java projects)
	 */
	
	public static final String NATURE_ID = "de.m_marvin.eclipsemeta.metaNature";
	public static final String TASK_CONFIG_NODE = "metaTaskConfig";
	
	public static final String JAVA_NATURE_ID = "org.eclipse.jdt.core.javanature";
	public static final String C_NATURE_ID = "org.eclipse.cdt.core.cnature";
	public static final String CPP_NATURE_ID = "org.eclipse.cdt.core.ccnature";
	
	public static final WorkingCopyOwner BUILDFILE_WORKING_COPY_OWNER = new WorkingCopyOwner() {};
	
	public static enum MetaState {
		OK,UNLOADED,ERROR;
	}
	
	public static enum RefreshType {
		RELOAD,REFRESH,REFRESH_DEPENDENCIES;
	}
	
	public static class TaskConfiguration {
		
		private String name;
		private Set<MetaTask<MetaProjectNature>> tasks = new HashSet<MetaTask<MetaProjectNature>>();
		
		public TaskConfiguration(String name) {
			this.name = name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		public Set<MetaTask<MetaProjectNature>> getTasks() {
			return tasks;
		}
		
		@Override
		public String toString() {
			return this.name;
		}
		
	}
	
	protected Set<TaskConfiguration> configurations = new HashSet<MetaProjectNature.TaskConfiguration>();
	protected TaskConfiguration activeConfiguration = null;
	
	protected IProject project;
	protected IEclipsePreferences settings;
	protected IMeta meta;
	
	protected final List<MetaTask<MetaProjectNature>> tasks = new ArrayList<>();
	protected final List<MetaGroup<MetaProjectNature>> groups = new ArrayList<>();
	protected final List<File> buildfileClasspath = new ArrayList<File>();
	protected MetaState state = MetaState.UNLOADED;
	
	@Override
	public void configure() throws CoreException {}

	@Override
	public void deconfigure() throws CoreException {}
	
	@Override
	public String toString() {
		return String.format("MetaProjectNature{project=%s,configurations=%s,activeConfiguration=%s,tasks=%s}", this.project, this.configurations, this.activeConfiguration, this.tasks);
	}
	
	public void loadTaskConfigs() throws BackingStoreException {

		this.configurations.clear();
		Preferences configSettings = this.settings.node(TASK_CONFIG_NODE);
		for (String configName : configSettings.keys()) {
			TaskConfiguration config = new TaskConfiguration(configName);
			config.getTasks().addAll(
					Stream.of(configSettings.get(configName, "").split(";"))
					.map(n -> new MetaTask<MetaProjectNature>(this, Optional.empty(), n)) // We don't need to know the group of the task for this
					.toList());
			this.configurations.add(config);
		}
		String activeConfig = this.settings.get("activeConfig", null);
		if (activeConfig == null) {
			setActiveConfiguration(null);
		} else {
			Optional<TaskConfiguration> activeConfiguration = this.configurations.stream().filter(config -> config.getName().equals(activeConfig)).findFirst();
			if (activeConfig.isEmpty()) {
				setActiveConfiguration(null);
			} else {
				setActiveConfiguration(activeConfiguration.get());
			}
		}
		
	}
	
	public void saveTaskConfigs() throws BackingStoreException {
		
		Preferences configSettings = this.settings.node(TASK_CONFIG_NODE);
		for (var config : this.configurations) {
			configSettings.put(config.getName(), config.getTasks().stream().map(MetaTask::name).reduce((a, b) -> a + ";" + b).orElse(""));
		}
		this.settings.put("activeConfig", this.activeConfiguration.getName());
		this.settings.flush();
		
	}
	
	@Override
	public IProject getProject() {
		return this.project;
	}
	
	public Collection<File> getBuildfileClasspath() {
		return this.buildfileClasspath;
	}
	
	public IEclipsePreferences getSettings() {
		return settings;
	}
	
	public MetaState getState() {
		return state;
	}

	public void changeState(MetaState state) {
		if (state != this.state) {
			this.state = state;
			MetaUI.refreshViewers();
		}
	}
	
	@Override
	public void setProject(IProject project) {
		this.project = project;
		this.settings = new ProjectScope(project).getNode(NATURE_ID);
		try {
			loadTaskConfigs();
		} catch (BackingStoreException e) {
			MetaUI.openError("Unable to load configuration!", "Unable to load meta project settings from file!", e);
			e.printStackTrace();
		}
	}
	
	public boolean isJava() {
		try {
			return this.project.hasNature(JAVA_NATURE_ID);
		} catch (CoreException e) {
			return false;
		}
	}
	
	public boolean isCpp() {
		try {
			return this.project.hasNature(C_NATURE_ID) || this.project.hasNature(CPP_NATURE_ID);
		} catch (CoreException e) {
			return false;
		}
	}
	
	public Set<TaskConfiguration> getConfigurations() {
		return configurations;
	}
	
	public TaskConfiguration getActiveConfiguration() {
		return activeConfiguration;
	}
	
	public void setActiveConfiguration(TaskConfiguration activeConfiguration) {
		this.activeConfiguration = activeConfiguration;
		refreshProject(RefreshType.RELOAD);
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
					MetaProjectNature.this.changeState(MetaState.ERROR);
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
						if (!success) {
							MetaProjectNature.this.changeState(MetaState.ERROR);
						} else {
							MetaProjectNature.this.changeState(MetaState.OK);
						}
					}
					
				});
				
				if (!setupBuildsystem()) {
					MetaProjectNature.this.changeState(MetaState.ERROR);
					MetaUI.openError("Meta Build", "Failed to initialize build file!");
				} else {

					if (monitor.isCanceled()) {
						freeMeta();
						return;
					}
					
					task.accept(SubMonitor.convert(monitor));
					
				}
				
				buildfileClasspath.clear();
				buildfileClasspath.addAll(this.meta.getBuildfileClasspath());
				
			} catch (Throwable e) {
				MetaProjectNature.this.changeState(MetaState.ERROR);
				MetaUI.openError(
					"Meta Build", "Error while trying to execute meta process!", e);
				e.printStackTrace();
			}
			
			freeMeta();

			MetaUI.refreshViewers();
			
		}).schedule();
		
	}
	
	public void runMetaTask(String... tasks) {
		
		runMeta(monitor -> {
			monitor.subTask("Excuting Tasks ...");
			this.meta.runTasks(tasks);
		});
		
	}
	
	public void refreshProject(RefreshType mode) {
		
		List<String> refreshTasks = this.activeConfiguration == null ? Collections.emptyList() : this.activeConfiguration.getTasks().stream().map(MetaTask::name).toList();
		
		runMeta(monitor -> {
			
			monitor.subTask("Loading Meta Tasks ...");
			
			this.tasks.clear();
			this.groups.clear();
			this.meta.getTasks(this, this.groups, this.tasks);
			MetaProjectNature.this.changeState(MetaState.OK);
			
			if (refreshTasks.isEmpty()) return;
			
			monitor.subTask("Run Configuration Tasks ...");
			
			this.meta.setSkipTaskRun(mode == RefreshType.RELOAD);
			this.meta.setRefreshDependencies(mode == RefreshType.REFRESH_DEPENDENCIES);
			if (!this.meta.runTasks(refreshTasks)) {
				MetaUI.openError("Update Meta Dependencies", "Failed to run dependency tasks!");
				MetaProjectNature.this.changeState(MetaState.ERROR);
				return;
			}
			
			monitor.subTask("Load Dependencies ...");
			
			processJavaSourceIncludes();
			processCppSourceIncludes();
			
		});
		
	}
	
	protected void processJavaSourceIncludes() {
		
		if (isJava()) {

			IJavaProject jp = JavaCore.create(this.project);
			if (jp == null) return;
			
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
			
	}
	
	protected void processCppSourceIncludes() {
		
		if (isCpp()) {

			ICProject cp = CoreModel.getDefault().create(this.project);
			if (cp == null) return;
			
			List<ICppSourceIncludes> cppIncludes = new ArrayList<>();
			this.meta.getSourceIncludes(cppIncludes, ICppSourceIncludes.CPP_LANGUAGE_ID);
			
			IPathEntry[] pathEntries = Stream.concat(
					cppIncludes.stream().flatMap(incl -> incl.getIncludeDirectories().stream().map(
							entry -> CoreModel.newIncludeEntry(null, null, IPath.fromFile(entry))
					)),
					cppIncludes.stream()
						.map(incl -> incl.getSymbols())
						.map(Map::entrySet).flatMap(Set::stream)
						.map(e -> CoreModel.newMacroEntry(null, e.getKey(), e.getValue()))
			).toArray(IPathEntry[]::new);
			
			try {
				cp.setRawPathEntries(pathEntries, null);
			} catch (CoreException e) {}
			
		}
			
	}
	
	public List<MetaTask<MetaProjectNature>> getMetaTasks() {
		return tasks;
	}

	public List<MetaGroup<MetaProjectNature>> getMetaGroups() {
		return groups;
	}
	
	/* Static helper methods */
	
	public static MetaProjectNature getSelectedProjectNature() {
		return getSelectedProjectNature(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
	}
	
	public static MetaProjectNature getSelectedProjectNature(IWorkbenchWindow window) {
		ISelection selection = window.getSelectionService().getSelection();
		if (selection instanceof ITreeSelection treeSelection) {
			if (treeSelection.getFirstElement() instanceof IAdaptable adaptable) {
				IResource resource = adaptable.getAdapter(IResource.class);
				if (resource != null) return getProjectNature(resource.getProject());
			}
			
		}
		return null;
	}
	
	public static MetaProjectNature getProjectNature(IProject project) {
		try {
			if (!project.hasNature(MetaProjectNature.NATURE_ID)) return null;
			return (MetaProjectNature) project.getNature(MetaProjectNature.NATURE_ID);
		} catch (CoreException e) {
			return null;
		}
	}
	
	public static void reloadAllMetaProjects() {
		for (var project : getAllMetaProjectNatures())
			project.refreshProject(RefreshType.RELOAD);
	}
	
	public static Collection<IProject> getAllMetaProjects() {
		return Stream.of(ResourcesPlugin.getWorkspace().getRoot().getProjects())
				.filter(p -> { try { return p.hasNature(MetaProjectNature.NATURE_ID); } catch (CoreException e) { return false; } })
				.toList();
	}
	
	public static Collection<MetaProjectNature> getAllMetaProjectNatures() {
		return Stream.of(ResourcesPlugin.getWorkspace().getRoot().getProjects())
				.map(p -> { try { return (MetaProjectNature) p.getNature(MetaProjectNature.NATURE_ID); } catch (CoreException e) { return (MetaProjectNature) null; } })
				.filter(n -> n != null)
				.toList();
	}
	
}
