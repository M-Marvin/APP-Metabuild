package de.m_marvin.eclipsemeta.ui.misc;

import org.eclipse.jface.viewers.ITreePathContentProvider;
import org.eclipse.jface.viewers.TreePath;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.metabuild.api.core.MetaGroup;
import de.m_marvin.metabuild.api.core.MetaTask;

public class MetaTaskContentProvider implements ITreePathContentProvider {
	
	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof MetaProjectNature[] nature) {
			return nature;
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
