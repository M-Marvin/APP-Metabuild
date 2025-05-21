package de.m_marvin.basicxml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import de.m_marvin.basicxml.internal.StackList;

public class XMLOutputStream implements XMLStream, AutoCloseable {

	/** output stream for XML character data */
	private final OutputStream stream;
	/** source XML reader for character data, null until XML version and charset configured or defaulting back to XML 1.0 and URF-8 */
	private Writer writer;
	/** XML version string for prolog entry */
	private String version = null;
	/** character encoding for prolog entry */
	private String encoding = null;
	
	private static record TagEntry(String name, Map<URI, String> previousNamespaces) {}
	
	private final boolean prettyPrinting;
	/** tag element stack, contains the "path" to the current element the parser is writing to */
	private final StackList<TagEntry> stack = new StackList<TagEntry>();
	/** the namespaces defined inside the element the parser is currently reading from */
	private Map<URI, String> namespaces = new HashMap<>();
	
	private final Function<URI, String> namespaceIdProvider;;
	
	private boolean singleLineText = true;
	
	public XMLOutputStream(OutputStream stream, boolean prettyPrinting, Function<URI, String> namespaceIdProvider) {
		Objects.requireNonNull(stream, "XML data stream can not be null");
		this.stream = stream;
		this.prettyPrinting = prettyPrinting;
		if (namespaceIdProvider == null) {
			this.namespaceIdProvider = url -> {
				// make random alphanumeric id for namespace
				String id;
				do {
					id = Integer.toHexString(Long.hashCode(System.nanoTime())).substring(0, 4);
				} while (this.namespaces.containsValue(id));
				return id;
			};
		} else {
			this.namespaceIdProvider = namespaceIdProvider;
		}
	}
	
	@Override
	public void close() throws IOException {
		this.writer.close();
		this.stream.close();
	}

	@Override
	public String xmlStackPath() {
		return this.stack.stream().map(TagEntry::name).reduce((a, b) -> a + "." + b).orElse("");
	}
	
	/**
	 * Sets the XML version written to the prolog entry in the XML file.<br>
	 * Has to be set before the first write operation is initiated, calls afterward have no effect.<br>
	 * NOTE: This does not affect how the stream operates, it behaves the same as with the default value 1.0.
	 * @param encoding The XML version to write into the XML file
	 */
	public void setVersion(String version) {
		if (this.writer != null) return;
		this.version = version;
	}
	
	/**
	 * Sets the character encoding used and written to the prolog entry in the XML file.<br>
	 * Has to be set before the first write operation is initiated, calls afterward have no effect.
	 * @param encoding The name of the character encoding to use
	 */
	public void setEncoding(String encoding) {
		if (this.writer != null) return;
		this.encoding = encoding;
	}
	
	/**
	 * Writes the prolog entry with XML version and character encoding at the top of the file.<br>
	 * Defaults to XML 1.0 and UTF-8 if no other values where set before this call or the first write operation on this stream.
	 * @throws IOException
	 */
	public void writeProlog() throws IOException {
		if (this.writer != null) return;
		
		// fallback to default versions
		if (this.version == null) this.version = "1.0";
		if (this.encoding == null) this.encoding = "UTF-8";
		
		// create writer
		try {
			this.writer = new OutputStreamWriter(this.stream, this.encoding);
		} catch (UnsupportedEncodingException e) {
			throw new IOException("unsupported encoding", e);
		}
		
		// create prolog element string
		ElementDescriptor element = new ElementDescriptor(DescType.OPEN, null, "xml", new LinkedHashMap<>());
		element.attributes().put("version", this.version);
		element.attributes().put("encoding", this.encoding);
		String prolog = makeElementString(element);
		
		// write prolog
		this.writer.write("<?" + prolog + "?>");
		
	}
	
	/** 
	 * formats the string between the angled brackets for the provided element descriptor
	 */
	public String makeElementString(ElementDescriptor element) {

		StringBuffer elementStr = new StringBuffer();
		if (element.type() == DescType.CLOSE)
			elementStr.append('/');
		
		HashMap<String, String> attributes = element.attributes() != null ? new LinkedHashMap<String, String>(element.attributes()) : new LinkedHashMap<String, String>();
		if (element.namespace() != null) {
			// if new namespace, register and define in attributes
			if (!this.namespaces.containsKey(element.namespace())) {
				this.namespaces.put(element.namespace(), this.namespaceIdProvider.apply(element.namespace()));
				attributes.put(String.format("xlmns:%s", this.namespaces.get(element.namespace())), element.namespace().toString());
			}
			// write namespace
			elementStr.append(this.namespaces.get(element.namespace())).append(':');
		}
		
		// write name and attributes
		elementStr.append(element.name());
		for (var a : attributes.entrySet()) {
			elementStr.append(String.format(" %s=\"%s\"", a.getKey(), replaceSpecialCharacters(a.getValue())));
		}
		
		if (element.type() == DescType.SELF_CLOSING)
			elementStr.append('/');
		
		return elementStr.toString();
		
	}
	
	protected static String replaceSpecialCharacters(String text) {
		text = text.replace("&", "&amp;");
		text = text.replace("<", "&lt;");
		text = text.replace(">", "&gt;");
		text = text.replace("'", "&apos;");
		text = text.replace("\"", "&quot;");
		return text;
	}

	/**
	 * Open the new tag element on the stack and clone the namespace map
	 */
	private void openTag(String name) {
		this.stack.push(new TagEntry(name, this.namespaces));
		this.namespaces = new HashMap<URI, String>(this.namespaces);
	}
	
	/**
	 * Close the current tag element on the stack and restore previous namespace map
	 */
	private void closeTag(String name) throws XMLException {
		if (this.stack.size() == 0)
			throw new XMLException(this, "excess close tag: </" + name + ">");
		TagEntry last = this.stack.pop();
		if (!last.name.equals(name))
			throw new XMLException(this, "improper tag close order: </" + name + "> should be </" + last.name() + ">");
		this.namespaces = last.previousNamespaces;
	}
	
	/**
	 * Writes the element tag for the element descriptor, ensuring that the order of element open and close tags is correct.
	 * @param element The element descriptor to write to the XML file
	 * @throws IOException
	 * @throws XMLException
	 */
	public void writeNext(ElementDescriptor element) throws IOException, XMLException {
		Objects.requireNonNull(element, "element can not be null");
		
		if (this.writer == null)
			writeProlog();
		
		if (element.type() == DescType.OPEN)
			openTag(element.name());
		else if (element.type() == DescType.CLOSE)
			closeTag(element.name());
		
		if (element.type() == DescType.CLOSE && element.attributes() != null && !element.attributes().isEmpty())
			throw new XMLException(this, "attributes should be empty on closing element: " + element.name());
		
		if (this.prettyPrinting && !this.singleLineText || element.type() != DescType.CLOSE) {
			this.writer.write('\n');
			for (int i = 0; i <  this.stack.size() - (element.type() == DescType.OPEN ? 1 : 0); i++)
				this.writer.write('\t');
		}
		
		this.writer.write("<" + makeElementString(element) + ">");
		
		// reset single line text to true if new element is opened, otherwise set to false since current element does obviously no longer contain only single line text
		this.singleLineText = element.type() == DescType.OPEN;
		
	}
	
	/**
	 * Writes the provided character data to the current elements XML data.
	 * @param cbuf The buffer containing the character data to write
	 * @param off The offset in the buffer from which to start to read the data
	 * @param len The number of characters to read from the buffer
	 * @param useCData If the characters should be written inside an CDATA block to the XML file
	 * @return The number of bytes actually written (should always match the len parameter, reserved for future changes)
	 * @throws IOException
	 * @throws XMLException 
	 */
	public int writeText(char[] cbuf, int off, int len, boolean useCData) throws IOException, XMLException {
		Objects.requireNonNull(cbuf, "character buffer can not be null");
		if ((off < 0) || (off > cbuf.length) || (len < 0) ||
			((off + len) > cbuf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}

		if (this.writer == null)
			writeProlog();
		
		if (this.stack.isEmpty())
			throw new XMLException("can not write text data ouside root XML element");

		// check if this text is single line or multi-line
		String text = new String(cbuf, off, len);
		this.singleLineText = this.singleLineText && !useCData && text.lines().count() == 1;
		
		// write text in new line if it's not just single line text
		if (!this.singleLineText)
			this.writer.write('\n');
		
		if (useCData) {
			
			// replace ]]> sequences with ]]&gt; to avoid corrupting CDATA block
			StringBuffer cdataText = new StringBuffer();
			int f = 0;
			int i;
			while ((i = text.indexOf("]]>", f)) > 0) {
				cdataText.append(text.substring(f, f - i));
				cdataText.append("]]&gt;");
				f = i + 3;
			}
			cdataText.append(text.substring(f));
			
			// write CDATA block
			this.writer.write("<![CDATA[" + cdataText + "]]>");
			
		} else {
			
			// write text to file
			this.writer.write(replaceSpecialCharacters(text));
			
		}
		
		return len;
	}
	
	/**
	 * Writes the provided string to the current elements XML data.
	 * @param text The text to be written
	 * @param useCData If the characters should be written inside an CDATA block to the XML file
	 * @throws IOException
	 * @throws XMLException 
	 */
	public void writeAllText(String text, boolean useCData) throws IOException, XMLException {
		char[] chars = text.toCharArray();
		writeText(chars, 0, chars.length, useCData);
	}
	
}
