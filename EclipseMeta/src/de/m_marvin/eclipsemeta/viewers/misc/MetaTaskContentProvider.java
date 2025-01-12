package de.m_marvin.eclipsemeta.viewers.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jface.viewers.ITreePathContentProvider;
import org.eclipse.jface.viewers.TreePath;

import de.m_marvin.eclipsemeta.MetaProjects;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.metabuild.api.core.MetaTask;

public class MetaTaskContentProvider implements ITreePathContentProvider {
	
	public static record MetaGroup(MetaProjectNature project, String group) {}
	
	@Override
	public Object[] getElements(Object inputElement) {
		List<Object> elements = new ArrayList<>();
		for (var p : MetaProjects.getAllMetaProjectNatures()) {
			elements.add(p);
			elements.addAll(p.getMetaTasks());
			elements.addAll(p.getMetaTasks().stream().map(m -> m.group()).toList());
		}
		return elements.toArray();
	}

	@Override
	public Object[] getChildren(TreePath parentPath) {
		Object r = parentPath.getFirstSegment();
		Object p = parentPath.getLastSegment();
		if (p instanceof MetaProjectNature nature) {
			return nature.getMetaTasks().stream().flatMap(t -> Stream.of(t.group(), t)).toArray();
		} else if (p instanceof String group && r instanceof MetaProjectNature nature) {
			return nature.getMetaTasks().stream().filter(t -> t.group().equals(group)).toArray();
		}
		return new Object[0];
	}

	@Override
	public boolean hasChildren(TreePath path) {
		Object r = path.getFirstSegment();
		Object p = path.getLastSegment();
		if (p instanceof MetaProjectNature nature) {
			return !nature.getMetaTasks().isEmpty();
		} else if (p instanceof String group && r instanceof MetaProjectNature nature) {
			return nature.getMetaTasks().stream().filter(t -> t.group().equals(group)).count() > 0;
		}
		return false;
	}

	@Override
	public TreePath[] getParents(Object element) {
		if (element instanceof MetaTask task) {
			Optional<MetaProjectNature> nature = MetaProjects.getAllMetaProjectNatures().stream().filter(n -> n.getMetaTasks().contains(task)).findAny();
			if (nature.isEmpty()) return new TreePath[0];
			return new TreePath[] {new TreePath(new Object[] { nature, task.group() })};
		} else if (element instanceof String group) {
			Optional<MetaProjectNature> nature = MetaProjects.getAllMetaProjectNatures().stream().filter(n -> n.getMetaTasks().stream().filter(t -> t.group().equals(group)).count() > 0).findAny();
			if (nature.isEmpty()) return new TreePath[0];
			return new TreePath[] {new TreePath(new Object[] { nature })};
		}
		return new TreePath[0];
	}
	
}
