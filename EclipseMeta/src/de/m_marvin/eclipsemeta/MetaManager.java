package de.m_marvin.eclipsemeta;

import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.wrapper.MetaWrapper;

public class MetaManager {
	
	public static final String WRAPPER_PROJECT_LOCATION = "meta/meta_wraooer.jar";
	public static final String META_WRAPPER_CLASS = "MetaWrapper";
	
	public static Optional<IMeta> claimMeta() {
		
		MetaWrapper.metaVersion = "0.1_build0"; // TODO version management
		
		if (!MetaWrapper.prepareMetabuild()) {
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					"Meta Launch", "Could not prepare meta installation for version: " + MetaWrapper.metaVersion);
			return Optional.empty();
		}
		
		try {
			return Optional.of(MetaWrapper.getMetabuild());
		} catch (Throwable e) {
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					"Meta Launch", e.getMessage());
			e.printStackTrace();
			return Optional.empty();
		}
		
	}
	
	public static void freeMeta(IProject project, IMeta meta) {
		meta.terminate();
	}
	
}
