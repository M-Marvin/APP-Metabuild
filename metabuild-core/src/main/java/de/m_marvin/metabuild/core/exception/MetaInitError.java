package de.m_marvin.metabuild.core.exception;

public class MetaInitError extends Error {
	
	private static final long serialVersionUID = -3955114496600191263L;

	public MetaInitError() {
		super();
	}
	
	public MetaInitError(String message) {
		super(message);
	}
	
	public MetaInitError(Throwable cause) {
		super(cause);
	}
	
	public MetaInitError(String message, Throwable cause) {
		super(message, cause);
	}

	public static MetaInitError msg(String message, Object... args) {
		return new MetaInitError(String.format(message, args));
	}
	
	public static MetaInitError msg(Throwable cause, String message, Object... args) {
		return new MetaInitError(String.format(message, args), cause);
	}
	
}
