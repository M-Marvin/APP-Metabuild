package de.m_marvin.eclipsemeta;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;

public class MetaProjects implements IStartup {
	
	public static final List<Object> PROJECT_SELECTOR_IDS = Arrays.asList(
			"org.eclipse.ui.navigator.ProjectExplorer",
			"org.eclipse.jdt.ui.PackageExplorer",
			"org.eclipse.cdt.ui.CView"
	);
	
	protected static IProject activeProject;
	protected static IPath activeItem;
	
	public static IPath getActiveItem() {
		return activeItem;
	}
	
	public static IProject getActiveProject() {
		return activeProject;
	}
	
	@Override
	public void earlyStartup() {
		
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().asyncExec(() -> {
				
			IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
			if (window != null) {
	
		    	 window.getSelectionService().addSelectionListener(new ISelectionListener() {
		 			
		 			@Override
		 			public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		 				
		 				if (PROJECT_SELECTOR_IDS.contains(part.getSite().getId())) {
		 					
		 					if (selection instanceof ITreeSelection treeSelection) {
		 						
		 						Object obj = treeSelection.getFirstElement();
		 						
		 						if (obj instanceof IAdaptable adaptable) {
		 							IResource resource = adaptable.getAdapter(IResource.class);
		 							if (resource == null) {
		 								System.err.println(adaptable);
		 								return;
		 							}
		 							activeProject = resource.getProject();
		 							activeItem = resource.getFullPath();
		 							refreshMetaProject(activeProject);
		 						}
		 						
		 					}
		 					
		 				}
		 				
		 			}
		 			
		 		});
		    	
			}
			   
		});
		
	}
	
	// TODO only for testing
	public void refreshMetaProject(IProject project) {
		try {
			if (!project.hasNature(MetaProjectNature.NATURE_ID)) return;
			
			MetaProjectNature nature = (MetaProjectNature) project.getNature(MetaProjectNature.NATURE_ID);
			nature.refreshProject();
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	public static Collection<IProject> getAllMetaProjects() {
		return Stream.of(ResourcesPlugin.getWorkspace().getRoot().getProjects())
				.filter(p -> { try { return p.hasNature(MetaProjectNature.NATURE_ID); } catch (CoreException e) { return false; } })
				.toList();
	}
	
	public static Collection<MetaProjectNature> getAllMetaProjectNatures() {
		return Stream.of(ResourcesPlugin.getWorkspace().getRoot().getProjects())
				.map(p -> { try { return (MetaProjectNature) p.getNature(MetaProjectNature.NATURE_ID); } catch (CoreException e) { return (MetaProjectNature) null; } })
				.filter(n -> n != null)
				.toList();
	}
	
}
