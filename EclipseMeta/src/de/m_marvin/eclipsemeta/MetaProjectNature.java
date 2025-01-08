package de.m_marvin.eclipsemeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

import de.m_marvin.eclipsemeta.meta.MetaManager;
import de.m_marvin.metabuild.api.core.IMeta;

public class MetaProjectNature implements IProjectNature {

	public static final String NATURE_ID = "de.m_marvin.eclipsemeta.projectNature";
	
	protected IProject project;
	protected IMeta meta;
	protected final List<String> metaTasks = new ArrayList<>();
	
	@Override
	public void configure() throws CoreException {
		// TODO Auto-generated method stub
		
		for (int i = 0; i < 10; i++) System.out.println("CONFIGURE");
		
	}

	@Override
	public void deconfigure() throws CoreException {
		// TODO Auto-generated method stub

		for (int i = 0; i < 10; i++) System.out.println("DECONFIGURE");
		
	}

	@Override
	public IProject getProject() {
		return this.project;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}
	
	public boolean claimMeta() {
		Optional<IMeta> meta = MetaManager.claimMeta();
		if (meta.isEmpty()) return false;
		this.meta = meta.get();
		return true;
	}
	
	public void freeMeta() {
		if (this.meta != null) return;
		MetaManager.freeMeta(this.project, this.meta);
		this.meta = null;
	}
	
	public boolean setupBuildsystem() {
		meta.setWorkingDirectory(this.project.getLocation().toFile());
		System.out.println(meta.workingDir());
		return meta.initBuild();
	}

	public void queryMetaTasks() {
		if (!claimMeta()) return;// TODO how to make an task delay if requirements not yet met ?
		
		if (!setupBuildsystem()) {
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
					"Meta Build", "Failed to initialize build file!");
		}
		
		System.out.println("QUERY TASKS");
		
		freeMeta();
	}
	
}
