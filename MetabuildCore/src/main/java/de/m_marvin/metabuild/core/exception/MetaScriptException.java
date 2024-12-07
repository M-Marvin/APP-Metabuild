package de.m_marvin.metabuild.core.exception;

import java.io.PrintWriter;

public class MetaScriptException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public MetaScriptException() {
		super();
	}
	
	public MetaScriptException(String message) {
		super(message);
	}
	
	public MetaScriptException(Throwable cause) {
		super(cause);
	}
	
	public MetaScriptException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public void printStack(PrintWriter s) {
		printStack(0, s);
	}
	
	protected void printStack(int l, PrintWriter s) {
		StringBuilder pad = new StringBuilder();
		for (int i = 0; i < l; i++) {
			pad.append("    ");
		}
		pad.append(l == 0 ? "==> " : "\\-> ");
		
		s.println(pad.toString() + getMessage());
		if (getCause() instanceof MetaScriptException e) {
			e.printStack(l + 1, s);
		} else if (getCause() != null) {
			getCause().printStackTrace(s);
		}
	}
	
}
