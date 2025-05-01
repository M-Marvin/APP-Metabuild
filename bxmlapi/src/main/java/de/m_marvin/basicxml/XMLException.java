package de.m_marvin.basicxml;

/**
 * Indicates an error while parsing XML data
 */
public class XMLException extends Exception {
	
	private static final long serialVersionUID = -7153809693116902097L;
	
	public XMLException() {
		super();
	}

	public XMLException(String msg) {
		super(msg);
	}

	public XMLException(String msg, Exception e) {
		super(msg, e);
	}
	
}
