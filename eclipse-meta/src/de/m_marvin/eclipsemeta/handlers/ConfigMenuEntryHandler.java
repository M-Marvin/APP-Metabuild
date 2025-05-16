package de.m_marvin.eclipsemeta.handlers;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.ui.misc.Icons;

public class ConfigMenuEntryHandler extends CompoundContributionItem {
	
	public static final String MENU_CONFIG_COMMAND_ID = "de.m_marvin.eclipsemeta.menu";
	
	@Override
	protected IContributionItem[] getContributionItems() {
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		MetaProjectNature nature = MetaProjectNature.getSelectedProjectNature(window);
		if (nature == null) return new IContributionItem[0];
		
		return nature.getConfigurations().stream().map(config -> {
			
			Map<Object, Object> params = new HashMap<>();
			params.put("configSelection", config.getName());
			boolean activeConfig = nature.getActiveConfiguration() == config;
			String entryName = config.getName() + (activeConfig ? " (active)" : "");
			CommandContributionItemParameter parameters = new CommandContributionItemParameter(
					window, 
					"test", 
					MetaCommandHandler.CHANGE_CONFIG_COMMAND, 
					params, 
					activeConfig ? Icons.TASK_CONFIGURATION_ACTIVE_ICON : Icons.TASK_CONFIGURATION_INACTIVE_ICON, 
					null, null, 
					entryName, 
					null, null, 
					CommandContributionItem.STYLE_PUSH, 
					null, true);
			return new CommandContributionItem(parameters);
			
		}).toArray(IContributionItem[]::new);
		
	}

}
