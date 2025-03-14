package de.m_marvin.eclipsemeta.ui.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature.RefreshType;
import de.m_marvin.eclipsemeta.ui.misc.Icons;
import de.m_marvin.eclipsemeta.ui.misc.MetaTaskContentProvider;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

public class MetaTaskView extends ViewPart {

	public static final String VIEW_ID = "de.m_marvin.eclipsemeta.taskView";
	
	protected TreeViewer viewer;
	protected TreeViewerColumn tasks;
	protected IAction reloadProjects;
	
	public void refreshProjects() {
		viewer.setInput(MetaProjectNature.getAllMetaProjectNatures().toArray(MetaProjectNature[]::new));
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		reloadProjects = new Action("Reload Tasks", Icons.REFRESH_ICON) {
			@Override
			public void run() {
				MetaProjectNature.reloadAllMetaProjects();
			}
		};
		reloadProjects.setToolTipText("Reloads all projects (runs config tasks in prepare phase only)");
		getViewSite().getActionBars().getToolBarManager().add(reloadProjects);
		
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new MetaTaskContentProvider());
		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);
		
		tasks = new TreeViewerColumn(viewer, SWT.NONE);
		tasks.getColumn().setText("Meta Tasks");
		tasks.getColumn().setWidth(300);
		tasks.setLabelProvider(new MetaTaskContentProvider.LabelProvider());
		
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				if (event.getSelection() instanceof IStructuredSelection ts) {
					if (ts.getFirstElement() instanceof MetaTask task) {
						if (task.ref() instanceof MetaProjectNature project) {
							project.runMetaTask(task.name());
						}
					} else if (ts.getFirstElement() instanceof MetaProjectNature project) {
						project.refreshProject(RefreshType.REFRESH);
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
