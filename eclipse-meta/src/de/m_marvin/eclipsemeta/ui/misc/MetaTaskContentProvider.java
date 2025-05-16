package de.m_marvin.eclipsemeta.ui.misc;

import java.util.Collection;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ITreePathContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.ViewerCell;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature.TaskConfiguration;
import de.m_marvin.metabuild.api.core.tasks.MetaGroup;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

public class MetaTaskContentProvider implements ITreePathContentProvider {
	
	public static class LabelProvider extends CellLabelProvider {
		
		@Override
		public void update(ViewerCell cell) {
			if (cell.getElement() instanceof MetaProjectNature project) {
				TaskConfiguration activeConfig = project.getActiveConfiguration();
				if (activeConfig != null) {
					cell.setText(project.getProject().getName() + " (" + activeConfig.getName() + ")");
				} else {
					cell.setText(project.getProject().getName() + " (no active configuration)");
				}
				switch (project.getState()) {
				case UNLOADED: cell.setImage(Icons.META_PROJECT_CLOSED_ICON.createImage()); break;
				case ERROR: cell.setImage(Icons.META_PROJECT_ERROR_ICON.createImage()); break;
				case OK: {
					if (project.isJava()) {
						cell.setImage(Icons.META_JAVA_PROJECT_LOADED_ICON.createImage());
					} else if (project.isCpp()) {
						cell.setImage(Icons.META_CPP_PROJECT_LOADED_ICON.createImage());
					} else {
						cell.setImage(Icons.META_PROJECT_LOADED_ICON.createImage());
					}
				}
				}
			} else if (cell.getElement() instanceof MetaGroup group) {
				cell.setText(group.group());
				cell.setImage(Icons.TASK_GROUP_ICON.createImage());
			} else if (cell.getElement() instanceof MetaTask task) {
				cell.setText(task.name());
				cell.setImage(Icons.TASK_ICON.createImage());
			} else {
				cell.setText(cell.getElement().toString());
			}
		}
		
	}
	
	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof Collection col) {
			return col.toArray();
		} else if (inputElement instanceof Object[] arr) {
			return arr;
		}
		return new Object[0];
	}

	@Override
	public Object[] getChildren(TreePath parentPath) {
		if (parentPath.getLastSegment() instanceof MetaProjectNature nature) {
			return nature.getMetaTasks().stream().map(t -> t.group().isPresent() ? t.group().get() : t).distinct().toArray();
		} else if (parentPath.getLastSegment() instanceof MetaGroup group) {
			MetaProjectNature nature = (MetaProjectNature) group.ref();
			return nature.getMetaTasks().stream().filter(t -> t.group().isPresent() && t.group().get() == group).toArray();
		}
		return new Object[0];
	}

	@Override
	public boolean hasChildren(TreePath path) {
		if (path.getLastSegment() instanceof MetaProjectNature nature) return !nature.getMetaTasks().isEmpty();
		if (path.getLastSegment() instanceof MetaGroup) return true;
		return false;
	}

	@Override
	public TreePath[] getParents(Object element) {
		if (element instanceof MetaProjectNature) {
			return new TreePath[] { 
				new TreePath(new Object[] { })
			};
		} else if (element instanceof MetaGroup group) {
			return new TreePath[] {
				new TreePath(new Object[] { group.ref() })
			};
		} else if (element instanceof MetaTask task) {
			return new TreePath[] {
				new TreePath(task.group().isPresent() ? new Object[] { task.ref(), task.group().get() } : new Object[] { task.ref() })
			};
		}
		
		return new TreePath[] {};
	}
	
}
