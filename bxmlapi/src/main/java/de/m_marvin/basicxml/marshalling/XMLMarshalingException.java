package de.m_marvin.basicxml.marshalling;

import de.m_marvin.basicxml.XMLStream;

/**
 * Indicates an error while parsing or writing XML data by an (un)marshaler
 */
public class XMLMarshalingException extends Exception {
	
	private static final long serialVersionUID = -7153809693116902097L;
	
	public XMLMarshalingException() {
		super();
	}

	public XMLMarshalingException(String msg) {
		super(msg);
	}

	public XMLMarshalingException(String msg, Exception e) {
		super(msg, e);
	}

	public XMLMarshalingException(XMLStream stream,String msg) {
		super(stream.xmlStackPath() + " : " + msg);
	}

	public XMLMarshalingException(XMLStream stream,String msg, Exception e) {
		super(stream.xmlStackPath() + " : " + msg, e);
	}
	
}
