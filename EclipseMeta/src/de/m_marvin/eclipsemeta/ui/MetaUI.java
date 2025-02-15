package de.m_marvin.eclipsemeta.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleOutputStream;

import de.m_marvin.eclipsemeta.ui.views.MetaTaskView;

public class MetaUI {
	
	public static String META_CONSOLE_NAME = "Meta";
	
	public static IOConsole getConsole() {
		IConsoleManager conMan = ConsolePlugin.getDefault().getConsoleManager();
		for (var console : conMan.getConsoles()) {
			if (console.getName().equals(META_CONSOLE_NAME)) return (IOConsole) console;
		}
		IOConsole console = new IOConsole(META_CONSOLE_NAME, null); // new MessageConsole(META_CONSOLE_NAME, ImageDescriptor.getMissingImageDescriptor());
		conMan.addConsoles(new IConsole[] { console });
		return console;
	}
	
	public static OutputStream newConsoleStream() {
		IOConsole console = getConsole();
		IOConsoleOutputStream out = console.newOutputStream();
		try {
			out.write(new String("\n \033[38;5;242m----------------------------------------------- \n\n").getBytes());
		} catch (IOException e) {}
		return out;
	}
	
	public static InputStream getConsoleInputStream() {
		IOConsole console = getConsole();
		return console.getInputStream();
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
