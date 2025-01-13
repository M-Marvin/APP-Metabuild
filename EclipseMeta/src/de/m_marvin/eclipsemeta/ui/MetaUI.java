package de.m_marvin.eclipsemeta.ui;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import de.m_marvin.eclipsemeta.ui.views.MetaTaskView;

// FIXME run task output does not show up in the console + IDE debugging integration
public class MetaUI {
	
	public static String META_CONSOLE_NAME = "Meta";
	
	public static MessageConsole getConsole() {
		IConsoleManager conMan = ConsolePlugin.getDefault().getConsoleManager();
		for (var console : conMan.getConsoles()) {
			if (console.getName().equals(META_CONSOLE_NAME)) return (MessageConsole) console;
		}
		MessageConsole console = new MessageConsole(META_CONSOLE_NAME, ImageDescriptor.getMissingImageDescriptor());
		conMan.addConsoles(new IConsole[] { console });
		return console;
	}
	
	public static MessageConsoleStream newConsoleStream() {
		MessageConsole console = getConsole();
		return console.newMessageStream();
	}
	
	public static void openError(String name, String message) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			MessageDialog.openError(PlatformUI.getWorkbench().getDisplay().getActiveShell(), name, message);
		});
	}

	public static void openError(String name, String message, IStatus status) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			ErrorDialog.openError(PlatformUI.getWorkbench().getDisplay().getActiveShell(), name, message, status);
		});
	}
	
	public static void refreshViewers() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			MetaTaskView taskView = (MetaTaskView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(MetaTaskView.VIEW_ID);
			taskView.refreshProjects();
		});
	}
	
}
