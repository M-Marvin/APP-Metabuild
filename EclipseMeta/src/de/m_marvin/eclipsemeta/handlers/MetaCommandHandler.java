package de.m_marvin.eclipsemeta.handlers;

import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.osgi.service.prefs.BackingStoreException;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature.RefreshType;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature.TaskConfiguration;
import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.eclipsemeta.ui.properties.MetaNaturePropertiesPage;

public class MetaCommandHandler extends AbstractHandler {
	
	public static final String REFRESH_COMMAND = "de.m_marvin.eclipsemeta.commands.refreshProject";
	public static final String REFRESH_DEPENDENCIES_COMMAND = "de.m_marvin.eclipsemeta.commands.refreshDependencies";
	public static final String CHANGE_CONFIG_COMMAND = "de.m_marvin.eclipsemeta.commands.changeProjectConfig";
	public static final String MANAGE_CONFIGS_COMMAND = "de.m_marvin.eclipsemeta.commands.manageProjectConfigs";
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		MetaProjectNature nature = MetaProjectNature.getSelectedProjectNature(window);
		if (nature == null) {
			MetaUI.openError("Meta Command Error", "Could not extecute meta command: %s", event.getCommand().getId());
			return null;
		}
		
		String cmd = event.getCommand().getId();
		switch (cmd) {
		
		case REFRESH_COMMAND:
			nature.refreshProject(RefreshType.REFRESH);
			break;
		case REFRESH_DEPENDENCIES_COMMAND:
			nature.refreshProject(RefreshType.REFRESH_DEPENDENCIES);
			break;
		case CHANGE_CONFIG_COMMAND:
			String configName = (String) event.getParameters().get("configSelection");
			Optional<TaskConfiguration> configuration = nature.getConfigurations().stream().filter(config -> config.getName().equals(configName)).findFirst();
			if (configuration.isEmpty()) {
				MetaUI.openError("Invalid Task Configuration", "Could not set the configuration '%s' as active, configuration does not exist!", configName);
			}
			nature.setActiveConfiguration(configuration.get());
			try {
				nature.saveTaskConfigs();
			} catch (BackingStoreException e) {
				MetaUI.openError("Failed to set Task Configuration", "Failed to save the new active task configuration settings!", e);
				System.err.println("Failed to save active task configuration settings!");
				e.printStackTrace();
			}
			MetaUI.refreshViewers();
			break;
		case MANAGE_CONFIGS_COMMAND:
			PreferencesUtil.createPropertyDialogOn(window.getShell(), nature.getProject(), MetaNaturePropertiesPage.PAGE_ID, null, null).open();
			break;
		}
		
		return null;
	}
	

}
