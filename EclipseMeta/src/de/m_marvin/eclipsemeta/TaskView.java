package de.m_marvin.eclipsemeta;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.internal.dialogs.ViewLabelProvider;
import org.eclipse.ui.part.ViewPart;

public class TaskView extends ViewPart {

	protected TableViewer viewer;
	
	public TaskView() {
		// TODO Auto-generated constructor stub
		
		
		
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		
		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		viewer.setInput(new String[] { "One", "Two", "Three" });
//		viewer.setLabelProvider(new ViewLabelProvider());
		
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
