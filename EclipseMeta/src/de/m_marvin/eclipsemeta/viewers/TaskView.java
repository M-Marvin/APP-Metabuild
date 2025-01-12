package de.m_marvin.eclipsemeta.viewers;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import de.m_marvin.eclipsemeta.MetaProjects;
import de.m_marvin.eclipsemeta.viewers.misc.MetaTaskContentProvider;
import de.m_marvin.eclipsemeta.viewers.misc.MetaTaskLabelProvider;

public class TaskView extends ViewPart {

	protected TreeViewer viewer;
	
	public TaskView() {
		// TODO Auto-generated constructor stub
		
		
		
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new MetaTaskContentProvider());
//		viewer.setLabelProvider(new MetaTaskLabelProvider());
		// TODO how to update this ?
		viewer.setInput(MetaProjects.getAllMetaProjectNatures());
		
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
