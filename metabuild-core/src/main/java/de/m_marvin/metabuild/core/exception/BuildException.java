package de.m_marvin.metabuild.core.exception;

public class BuildException extends MetaScriptException {
	
	private static final long serialVersionUID = 1000526149873382214L;

	public BuildException() {
		super();
	}
	
	public BuildException(String message) {
		super(message);
	}
	
	public BuildException(Throwable cause) {
		super(cause);
	}
	
	public BuildException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public static BuildException msg(String message, Object... args) {
		return new BuildException(String.format(message, args));
	}
	
	public static BuildException msg(Throwable cause, String message, Object... args) {
		return new BuildException(String.format(message, args), cause);
	}
	
}
