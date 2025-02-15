package de.m_marvin.eclipsemeta;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import org.eclipse.core.resources.IProject;

import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.wrapper.MetaWrapper;

public class MetaManager {
	
	public static final String WRAPPER_PROJECT_LOCATION = "meta/meta_wrapper.jar";
	public static final String META_WRAPPER_CLASS = "MetaWrapper";
	
	protected static class ClaimLock {
		public boolean claimed = false;
	}
	
	protected static ClaimLock lock = new ClaimLock();
	
	public static Optional<IMeta> claimMeta(IProject project) {

		synchronized (lock) {
			
			try {
				while (lock.claimed) lock.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return Optional.empty();
			}
			
			File projectRoot = project.getLocation().toFile();
			File wrapperFile = new File(projectRoot, MetaWrapper.WRAPPER_PROJECT_LOCATION);
			
			if (!wrapperFile.isFile()) {
				MetaUI.openError("Meta Launch", "Could not find meta wrapper in project: " + wrapperFile);
				return Optional.empty();
			}
			
			URL wrapperURL;
			try {
				wrapperURL = wrapperFile.toURI().toURL();
			} catch (MalformedURLException e) {
				MetaUI.openError("Meta Launch", "Could not construct path URL for wrapper: " + wrapperFile + "\n" + e.getMessage());
				return Optional.empty();
			}
			
			if (!MetaWrapper.prepareMetabuild(wrapperURL)) {
				MetaUI.openError("Meta Launch", "Could not prepare meta installation for version: " + MetaWrapper.metaVersion);
				return Optional.empty();
			}
			
			try {
				lock.claimed = true;
				return Optional.of(MetaWrapper.getMetabuild());
			} catch (Throwable e) {
				lock.claimed = false;
				MetaUI.openError("Meta Launch", e.getMessage());
				e.printStackTrace();
				return Optional.empty();
			}
		}
		
	}
	
	public static void freeMeta(IProject project, IMeta meta) {
		synchronized (lock) {
			meta.terminate();
			lock.claimed = false;
			lock.notify();
		}
	}
	
}
