package de.m_marvin.eclipsemeta;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

public class MetaProjectNature implements IProjectNature {

	public static final String NATURE_ID = "de.m_marvin.eclipsemeta.projectNature";
	
	protected IProject project;
	
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

}
