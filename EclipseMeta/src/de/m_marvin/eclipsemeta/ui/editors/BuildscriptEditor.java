package de.m_marvin.eclipsemeta.ui.editors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
	
	@Override
	protected ITypeRoot getInputJavaElement() {

		List<IClasspathEntry> classpath = new ArrayList<IClasspathEntry>();
		
		classpath.add(JavaRuntime.getDefaultJREContainerEntry());

		File buildsystemJar = new File("C:\\Users\\marvi\\.meta\\versions\\meta-0.1_build1\\metabuild-core.jar");
		classpath.add(JavaCore.newLibraryEntry(IPath.fromFile(buildsystemJar), null, null));
		
		try {
			return MetaProjectNature.BUILDFILE_WORKING_COPY_OWNER.newWorkingCopy(
					this.getEditorInput().getName(), 
					classpath.toArray(IClasspathEntry[]::new),
					null);
		} catch (JavaModelException e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
}
