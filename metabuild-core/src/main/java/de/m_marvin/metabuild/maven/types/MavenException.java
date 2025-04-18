package de.m_marvin.metabuild.maven.types;

public class MavenException extends Exception {
	
	private static final long serialVersionUID = -1703897118216993899L;

	public MavenException() {
		super();
	}

	public MavenException(String msg, Object... args) {
		super(String.format(msg, args));
	}

	public MavenException(Throwable e, String msg, Object... args) {
		super(String.format(msg, args), e);
	}
	
}
