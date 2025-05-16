package de.m_marvin.eclipsemeta.ui.editors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.ui.IEditorInput;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.ui.misc.Icons;

@SuppressWarnings("restriction")
public class BuildscriptEditor extends CompilationUnitEditor {
	
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		setTitleImage(Icons.META_FILE_ICON.createImage());
	}
	
	public Collection<IClasspathEntry> getClasspath() {
		List<IClasspathEntry> classpath = new ArrayList<IClasspathEntry>();
		classpath.add(JavaRuntime.getDefaultJREContainerEntry());
		IResource resource = getEditorInput().getAdapter(IResource.class);
		if (resource == null) return classpath;
		IProject project = resource.getProject();
		MetaProjectNature nature = MetaProjectNature.getProjectNature(project);
		if (nature == null) return classpath;
		for (File entryPath : nature.getBuildfileClasspath()) {
			classpath.add(JavaCore.newLibraryEntry(IPath.fromFile(entryPath), null, null));
		}
		return classpath;
	}
	
	@Override
	protected ITypeRoot getInputJavaElement() {
		try {
			return MetaProjectNature.BUILDFILE_WORKING_COPY_OWNER.newWorkingCopy(
					this.getEditorInput().getName(), 
					getClasspath().toArray(IClasspathEntry[]::new),
					null
			);
		} catch (JavaModelException e) {
			e.printStackTrace();
			return null;
		}	
	}
	
}
