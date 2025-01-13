package de.m_marvin.eclipsemeta.ui.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import de.m_marvin.eclipsemeta.misc.MetaProjects;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.ui.misc.MetaTaskContentProvider;
import de.m_marvin.metabuild.api.core.MetaGroup;
import de.m_marvin.metabuild.api.core.MetaTask;

public class MetaTaskView extends ViewPart {

	public static final String VIEW_ID = "de.m_marvin.eclipsemeta.taskView";
	
	protected TreeViewer viewer;
	protected TreeViewerColumn tasks;
	protected IAction reloadProjects;
	
	public void refreshProjects() {
		
		viewer.setInput(MetaProjects.getAllMetaProjectNatures().toArray(MetaProjectNature[]::new));
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		reloadProjects = new Action("Reload Tasks", ImageDescriptor.getMissingImageDescriptor()) {
			@Override
			public void run() {
				// TODO
				MetaProjects.refreshMetaProjects();
			}
		};
		getViewSite().getActionBars().getToolBarManager().add(reloadProjects);
		
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new MetaTaskContentProvider());
		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);
		
		tasks = new TreeViewerColumn(viewer, SWT.NONE);
		tasks.getColumn().setText("Meta Tasks");
		tasks.getColumn().setWidth(300);
		tasks.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				if (cell.getElement() instanceof MetaProjectNature project) {
					cell.setText(project.getProject().getName());
				} else if (cell.getElement() instanceof MetaGroup group) {
					cell.setText(group.group());
				} else if (cell.getElement() instanceof MetaTask task) {
					cell.setText(task.name());
				} else {
					cell.setText(cell.getElement().toString());
				}
			}
		});
		
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				if (event.getSelection() instanceof IStructuredSelection ts) {
					if (ts.getFirstElement() instanceof MetaTask task) {
						if (task.ref() instanceof MetaProjectNature project) {
							MetaProjects.runMetaTask(project, task.name());
						}
					} else if (ts.getFirstElement() instanceof MetaProjectNature project) {
						project.refreshProject();
					}
				}
			}
		});
		
	}
	
	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
