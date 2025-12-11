package de.m_marvin.eclipsemeta.ui.properties;

import java.util.Collection;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.PropertyPage;
import org.osgi.service.prefs.BackingStoreException;

import de.m_marvin.eclipsemeta.natures.MetaProjectNature;
import de.m_marvin.eclipsemeta.natures.MetaProjectNature.TaskConfiguration;
import de.m_marvin.eclipsemeta.ui.MetaUI;
import de.m_marvin.eclipsemeta.ui.misc.Icons;
import de.m_marvin.eclipsemeta.ui.misc.MetaTaskContentProvider;
import de.m_marvin.metabuild.api.core.tasks.MetaTask;

public class MetaNaturePropertiesPage extends PropertyPage implements IWorkbenchPropertyPage {

	public static final Pattern CONFIG_NAME_FILTER = Pattern.compile("[\\w\\s\\d]+");

	public static final String PAGE_ID = "de.m_marvin.eclipsemeta.ui.properties.metaPage";
	
	protected Combo configuration;
	protected Button createConfiguration;
	protected Button deleteConfiguration;
	protected TreeViewer taskList;
	protected TreeViewer buildTasks;
	protected Button addBuildTaskButton;
	protected Button removeBuildTaskButton;
	protected MetaProjectNature nature;
	
	protected Composite createBackgroundComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL);
		data.grabExcessHorizontalSpace = true;
		composite.setLayoutData(data);
		return composite;
	}
	
	protected void addConfigurationGroup(Composite parent) {
		
		Group group = new Group(parent, SWT.NULL);
		GridData groupLayout = new GridData();
		groupLayout.horizontalAlignment = GridData.FILL;
		groupLayout.grabExcessHorizontalSpace = true;
		groupLayout.horizontalSpan = 3;
		group.setLayoutData(groupLayout);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		group.setLayout(layout);

		this.configuration = new Combo(group, SWT.DROP_DOWN);
		GridData topLayout = new GridData();
		topLayout.horizontalAlignment = GridData.FILL;
		topLayout.grabExcessHorizontalSpace = true;
		topLayout.horizontalSpan = 2;
		this.configuration.setLayoutData(topLayout);
		
		
		this.createConfiguration = new Button(group, SWT.NULL);
		this.createConfiguration.setText("Create Task Configuration");

		this.deleteConfiguration = new Button(group, SWT.NULL);
		this.deleteConfiguration.setText("Delete Task Configuration");
		
	}
	
	protected void addTaskLists(Composite parent) {
		
		GridData listLayout = new GridData();
		listLayout.verticalAlignment = GridData.FILL;
		listLayout.horizontalAlignment = GridData.FILL;
		listLayout.grabExcessVerticalSpace = true;
		
		Tree buildTasksTree = new Tree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		buildTasksTree.setLayoutData(listLayout);
		this.buildTasks = new TreeViewer(buildTasksTree);
		this.buildTasks.setContentProvider(new MetaTaskContentProvider());
		this.buildTasks.getTree().setHeaderVisible(true);
		this.buildTasks.getTree().setLinesVisible(true);
		
		TreeViewerColumn list1 = new TreeViewerColumn(this.buildTasks, SWT.NONE);
		list1.getColumn().setText("Configuration Meta Tasks");
		list1.getColumn().setWidth(300);
		list1.setLabelProvider(new MetaTaskContentProvider.LabelProvider());
		
		Composite buttons = new Composite(parent, SWT.NULL);
		buttons.setLayout(new GridLayout());
		this.addBuildTaskButton = new Button(buttons, SWT.NONE);
		this.addBuildTaskButton.setText("<<");
		this.removeBuildTaskButton = new Button(buttons, SWT.NONE);
		this.removeBuildTaskButton.setText(">>");
		
		Tree taskListTree = new Tree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		taskListTree.setLayoutData(listLayout);
		this.taskList = new TreeViewer(taskListTree);
		this.taskList.setContentProvider(new MetaTaskContentProvider());
		this.taskList.getTree().setHeaderVisible(true);
		this.taskList.getTree().setLinesVisible(true);
		
		TreeViewerColumn list2 = new TreeViewerColumn(this.taskList, SWT.NONE);
		list2.getColumn().setText("Available Meta Tasks");
		list2.getColumn().setWidth(300);
		list2.setLabelProvider(new MetaTaskContentProvider.LabelProvider());
		
	}
	
	protected void addInfo(Composite parent) {

		Composite info = new Composite(parent, SWT.NULL);
		GridData infoLayout = new GridData();
		infoLayout.horizontalSpan = 3;
		info.setLayoutData(infoLayout);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		info.setLayout(layout);
		
		Label infoIcon = new Label(info, SWT.NULL);
		infoIcon.setImage(Icons.INFO_ICON_IMAGE);

		Label infoText = new Label(info, SWT.NULL);
		infoText.setText("The configured tasks will be run in \"prepare phase only\", when loading/reloading the project, to get information such as dependencies to include.\nThe tasks will be actualy executed when the meta project is refreshed.\nThe tasks are executed with the refresh dependencies flag, when calling refresh dependencies on the project.\nAll three only apply when the configuration is active while loading/refreshing the project.");
		
	}
	
	protected void updateConfigList() {
		String[] items = nature.getConfigurations().stream().map(TaskConfiguration::getName).distinct().toArray(String[]::new);
		this.configuration.setItems(items);
	}
	
	protected void selectConfiguration(String configName) {
		for (var config : nature.getConfigurations()) {
			if (config.getName().equals(configName)) {
				loadConfiguration(config);
				return;
			}
		}
	}
	
	protected void loadProjectMetaTasks() {
		this.taskList.setInput(new MetaProjectNature[] {nature});
	}
	
	protected void loadConfiguration(TaskConfiguration config) {

		if (config == null) {
			
			if (!this.configuration.getText().isEmpty())
				this.configuration.setText("");

			this.buildTasks.setInput(new Object[0]);
			this.buildTasks.getTree().clearAll(true);
			this.addBuildTaskButton.setEnabled(false);
			this.removeBuildTaskButton.setEnabled(false);
			
		} else {

			if (!this.configuration.getText().equals(config.getName()))
				this.configuration.setText(config.getName());
			
			this.buildTasks.setInput(config.getTasks());
			this.addBuildTaskButton.setEnabled(true);
			this.removeBuildTaskButton.setEnabled(true);
			
		}
		
	}
	
	@Override
	public boolean performCancel() {
		if (this.nature == null) return true;
		try {
			this.nature.loadTaskConfigs();
		} catch (BackingStoreException e) {
			MetaUI.openError("Unable to restore config!", "Unable to load back meta project settings from filesystem!", e);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	@Override
	public boolean performOk() {
		if (this.nature == null) return true;
		try {
			this.nature.saveTaskConfigs();
		} catch (BackingStoreException e) {
			MetaUI.openError("Unable to store config!", "Unable to save meta project settings to filesystem!", e);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	@Override
	protected void performApply() {
		if (this.nature == null) return;
		try {
			this.nature.saveTaskConfigs();
		} catch (BackingStoreException e) {
			MetaUI.openError("Unable to store config!", "Unable to save meta project settings to filesystem!", e);
			e.printStackTrace();
		}
	}
	
	@Override
	protected Control createContents(Composite parent) {
		
		// Create UI
		noDefaultButton();
		Composite composite = createBackgroundComposite(parent);
		addConfigurationGroup(composite);
		addTaskLists(composite);
		addInfo(composite);
		
		if (getElement() instanceof IAdaptable adaptable) {
			IProject project = adaptable.getAdapter(IProject.class);
			if (project == null) {
				MetaUI.openError("Meta properties invalid", "Meta properties can not be used on non project object: %s", adaptable.getClass().getName());
			}
			try {
				this.nature = (MetaProjectNature) project.getNature(MetaProjectNature.NATURE_ID);
			} catch (CoreException e) {
				MetaUI.openError("Meta properties invalid", "Meta properties can not be used on non meta project: %s", project.getName());
			}
		} else {
			MetaUI.openError("Meta properties invalid", "Meta properties can not be used on non project object: %s", getElement().getClass().getName());
		}
		
		// Load configurations and project meta tasks
		if (this.nature == null) {
			loadConfiguration(null);
			return composite;
		}
		
		var active = this.nature.getActiveConfiguration();
		updateConfigList();
		loadConfiguration(active);
		loadProjectMetaTasks();
		
		this.configuration.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				// Try to load tasks for selected configuration
				selectConfiguration(MetaNaturePropertiesPage.this.configuration.getText());
			}
		});
		this.createConfiguration.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String configName = MetaNaturePropertiesPage.this.configuration.getText();
				if (!CONFIG_NAME_FILTER.matcher(configName).matches()) {
					MessageDialog.openError(getShell(), "Invalid Configuration Name", String.format("The name '%s' is not a valid name!", configName));
					return;
				}
				
				for (var config : nature.getConfigurations()) {
					if (config.getName().equals(configName)) {
						MessageDialog.openError(getShell(), "Invalid Configuration Name", String.format("An configuration named '%s' already exists!", configName));
						return;
					}
				}
				
				// Create new configuration
				nature.getConfigurations().add(new TaskConfiguration(configName));
				updateConfigList();
				selectConfiguration(configName);
			}
		});
		this.deleteConfiguration.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String configName = MetaNaturePropertiesPage.this.configuration.getText();
				for (var config : nature.getConfigurations()) {
					if (config.getName().equals(configName)) {
						if (MessageDialog.openConfirm(getShell(), "Delete Task Configuration", String.format("Delete task configuration '%s'?", configName))) {
							// Delete configuration
							nature.getConfigurations().remove(config);
							updateConfigList();
							loadConfiguration(null);
							return;
						} else {
							return;
						}
					}
				}
				MessageDialog.openError(getShell(), "Invalid Configuration Name", String.format("No configuration named '%s' does exist!", configName));
			}
		});
		this.addBuildTaskButton.addSelectionListener(new SelectionAdapter() {
			@SuppressWarnings("unchecked")
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Transfer selected meta tasks to configuration list
				ITreeSelection selection = MetaNaturePropertiesPage.this.taskList.getStructuredSelection();
				if (MetaNaturePropertiesPage.this.buildTasks.getInput() instanceof Collection col) {
					col.addAll(selection.stream().filter(MetaTask.class::isInstance).toList());
					MetaNaturePropertiesPage.this.buildTasks.setInput(col);
				}
			}
		});
		this.removeBuildTaskButton.addSelectionListener(new SelectionAdapter() {
			@SuppressWarnings("unchecked")
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Remove selected meta tasks from configuration list
				ITreeSelection selection = MetaNaturePropertiesPage.this.buildTasks.getStructuredSelection();
				if (MetaNaturePropertiesPage.this.buildTasks.getInput() instanceof Collection col) {
					col.removeAll(selection.stream().toList());
					MetaNaturePropertiesPage.this.buildTasks.setInput(col);
				}
			}
		});
		
		return composite;
	}

}
