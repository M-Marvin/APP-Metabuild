package de.m_marvin.metabuild.core.exception;

public class BuildScriptException extends MetaScriptException {
	
	private static final long serialVersionUID = -4080799349637983571L;

	public BuildScriptException() {
		super();
	}
	
	public BuildScriptException(String message) {
		super(message);
	}
	
	public BuildScriptException(Throwable cause) {
		super(cause);
	}
	
	public BuildScriptException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public static BuildScriptException msg(String message, Object... args) {
		return new BuildScriptException(String.format(message, args));
	}
	
	public static BuildScriptException msg(Throwable cause, String message, Object... args) {
		return new BuildScriptException(String.format(message, args), cause);
	}
	
}
