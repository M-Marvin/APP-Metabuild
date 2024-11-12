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
		
		s.println("test");
		
	}
	
}
