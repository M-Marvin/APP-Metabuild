package de.m_marvin.eclipsemeta.ui.misc;

import java.net.URL;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.osgi.framework.Bundle;

public class Icons {
	
	public static final Bundle BUNDLE = Platform.getBundle("de.m_marvin.eclipsemeta");
	
	private static ImageDescriptor img(String path) {
		URL iconURL = BUNDLE.getEntry(path);
		if (iconURL == null) return ImageDescriptor.getMissingImageDescriptor();
		return ImageDescriptor.createFromURL(iconURL);
	}
	
	public static final ImageDescriptor META_ICON = img("icons/obj16/meta.png");
	public static final ImageDescriptor META_FILE_ICON = img("icons/obj16/meta_file.png");
	
	public static final ImageDescriptor INFO_ICON = img("icons/obj16/info.png");
	public static final ImageDescriptor REFRESH_ICON = img("icons/obj16/refresh.png");
	public static final ImageDescriptor TASK_CONFIGURATIONS_ICON = img("icons/obj16/task_configurations.png");
	public static final ImageDescriptor TASK_CONFIGURATION_ACTIVE_ICON = img("icons/obj16/task_configuration_active.png");
	public static final ImageDescriptor TASK_CONFIGURATION_INACTIVE_ICON = img("icons/obj16/task_configuration_inactive.png");
	public static final ImageDescriptor MANAGE_CONFIGURATIONS_ICON = img("icons/obj16/manage_configurations.png");
	
	public static final ImageDescriptor META_PROJECT_ERROR_ICON = img("icons/obj16/faulty_project.png");
	public static final ImageDescriptor META_PROJECT_CLOSED_ICON = img("icons/obj16/closed_project.png");
	public static final ImageDescriptor META_PROJECT_LOADED_ICON = img("icons/obj16/open_project.png");
	public static final ImageDescriptor META_JAVA_PROJECT_LOADED_ICON = img("icons/obj16/open_project_java.png");
	public static final ImageDescriptor META_CPP_PROJECT_LOADED_ICON = img("icons/obj16/open_project_cpp.png");
	public static final ImageDescriptor TASK_GROUP_ICON = img("icons/obj16/task_group.png");
	public static final ImageDescriptor TASK_ICON = img("icons/obj16/task.png");
	
}
