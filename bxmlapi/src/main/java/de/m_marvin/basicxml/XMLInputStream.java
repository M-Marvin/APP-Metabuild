package de.m_marvin.basicxml;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.m_marvin.basicxml.internal.StackList;

/**
 * An XML character data input stream, capable of reading individual elements in order in which they are supplied from the input stream.<br>
 * Text data within and between individual tags is read separately from the tag descriptors.
 */
public class XMLInputStream implements XMLStream, AutoCloseable {
	
	/** source stream for XML character data */
	private final InputStream stream;
	/** indicates that this stream was split from an parent stream **/
	private final boolean isSplit;
	/** source XML reader for character data, null until prolog read or defaulting to XML 1.0 UTF-8 */
	private Reader reader;
	/** XML version string from prolog entry */
	private String version = null;
	/** character encoding from prolog entry */
	private String encoding = null;
	
	private static record TagEntry(String name, Map<String, URI> previousNamespaces) {}
	
	/** character data buffer for parsing from stream */
	private final StringBuffer buffer = new StringBuffer();
	/** tag element stack, contains the "path" to the current element the parser is reading from */
	private final StackList<TagEntry> stack = new StackList<TagEntry>();
	/** the namespaces defined inside the element the parser is currently reading from */
	private Map<String, URI> namespaces = new HashMap<>();
	
	public XMLInputStream(InputStream stream) throws IOException {
		Objects.requireNonNull(stream, "XML data stream can not be null");
		this.stream = stream;
		this.isSplit = false;
	}
	
	private XMLInputStream(XMLInputStream parentStream) {
		this.stream = parentStream.stream;
		this.reader = parentStream.reader;
		this.version = parentStream.version;
		this.encoding = parentStream.encoding;
		this.namespaces = parentStream.namespaces;
		this.stack.add(parentStream.stack.peek());
		this.isSplit = true;
	}
	
	@Override
	public void close() throws IOException {
		this.stream.close();
	}
	
	/**
	 * Attempts to fill the character buffer for parsing XML with the requested number of characters from the source stream or reader.<br>
	 * If the source reader is not yet set, it will assume ASCII 1 byte per character.
	 */
	private void bufferData(int blen) throws IOException {
		if (this.buffer.length() <= blen) {
			int n = blen - this.buffer.length();
			if (this.reader != null) {
				char[] c = new char[n];
				int r = this.reader.read(c);
				if (r == -1) r = 0;
				this.buffer.append(c, 0, r);
				if (r < n) throw new EOFException("unexpected EOF");
			} else {
				// for prolog reading, assume 1 byte per character
				byte[] b = this.stream.readNBytes(n);
				if (b.length != n) throw new EOFException("unexpected EOF");
				this.buffer.append(new String(b, StandardCharsets.US_ASCII));
			}
		}
	}
	
	/**
	 * Read the character at the index from the current character buffer
	 */
	private char readAt(int index) throws IOException {
		bufferData(index + 1);
		return this.buffer.charAt(index);
	}
	
	/**
	 * Return the index of the first occurrence of the character in the character buffer
	 */
	private int findFirst(char c) throws IOException {
		int i = 0;
		while (readAt(i) != c) i++;
		return i;
	}
	
	/**
	 * Read the requested number of characters from the character buffer
	 */
	private String readN(int len) throws IOException {
		bufferData(len);
		return this.buffer.substring(0, len);
	}
	
	/**
	 * Delete the requested number of characters from the character buffer
	 */
	private void deleteN(int len) {
		this.buffer.delete(0, len);
	}
	
	/**
	 * Open the new tag element on the stack and clone the namespace map
	 */
	private void openTag(String name) {
		this.stack.push(new TagEntry(name, this.namespaces));
		this.namespaces = new HashMap<String, URI>(this.namespaces);
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
	 * Attempt to read the prolog entry, put the read characters onto the read buffer if this fails<br>
	 * Default to XML 1.0 and UTF-8 if no prolog could be read.
	 */
	private void readProlog() throws IOException, XMLException {
		// Attempt to read prolog
		if (readN(5).equals("<?xml")) {
			int i = findFirst('>') + 1;
			String prolog = readN(i).substring(2, i - 2);
			deleteN(i);
			
			ElementDescriptor element = parseElementString(prolog);
			if (element.type != DescType.OPEN)
				throw new XMLException("prolog entry can not be closing or self closing: " + prolog);
			this.stack.clear(); // remove the "xml" element opened by the prolog entry
			
			this.version = element.attributes.get("version");
			this.encoding = element.attributes.get("encoding");
		}
		
		// fallback to default versions
		if (this.version == null) this.version = "1.0";
		if (this.encoding == null) this.encoding = "UTF-8";
		
		try {
			this.reader = new BufferedReader(new InputStreamReader(this.stream, this.encoding));
		} catch (UnsupportedEncodingException e) {
			throw new IOException("unsupoerted encoding in prolog", e);
		}
	}
	
	/**
	 * Returns the XML version specified in the files prolog entry.
	 * @return The version string specified in XML or the fallback version "1.0" if no prolog or version attribute was specified
	 * @throws IOException If an IO exception occurred while accessing the source stream
	 * @throws XMLException If an exception occurred while parsing the XML content
	 */
	public String getVersion() throws IOException, XMLException {
		if (this.version == null || this.encoding == null)
			readProlog();
		return version;
	}
	
	/**
	 * Returns the character encoding specified in the files prolog entry.
	 * @return The character encoding name specified in XML or the fallback encoding "UTF-8" if no prolog or encoding attribute was specified
	 * @throws IOException If an IO exception occurred while accessing the source stream
	 * @throws XMLException If an exception occurred while parsing the XML content
	 */
	public String getEncoding() throws IOException, XMLException {
		if (this.version == null || this.encoding == null)
			readProlog();
		return encoding;
	}
	
	private static final Pattern ELEMENT_NAME = Pattern.compile("^(?:([a-zA-Z_][a-zA-Z0-9\\-_.]*):|)([a-zA-Z_][a-zA-Z0-9\\-_.]*)");
	private static final Pattern ATTRIBUTE = Pattern.compile("((?:[a-zA-Z_][a-zA-Z0-9\\-_.]*:|)[a-zA-Z_][a-zA-Z0-9\\-_.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
	private static final Pattern NAMESPACE = Pattern.compile("(?i)xmlns(?::([a-zA-Z_][a-zA-Z0-9\\-_.]*)|)");
	
	/**
	 * Describes if the element was opened, closed or is self closing
	 */
	public static enum DescType {
		OPEN,
		CLOSE,
		SELF_CLOSING
	}
	
	/**
	 * Describes an element that was parsed from the XML file
	 */
	public static record ElementDescriptor(DescType type, URI namespace, String name, Map<String, String> attributes) {

		public boolean isSameField(ElementDescriptor other) {
			return Objects.equals(other.namespace, namespace) && Objects.equals(other.name, name);
		}
		
		@Override
		public final String toString() {
			return "namespace: " + this.namespace + " element: " + this.name;
		}
		
	}
	
	/**
	 * Parses the string between the angled brackets of an tag element and returns the element descriptor.
	 */
	private ElementDescriptor parseElementString(String elementStr) throws IOException, XMLException {
		String s = elementStr;
		
		// check type of tag
		boolean closing = elementStr.startsWith("/");
		boolean selfClosing = elementStr.endsWith("/");
		if (closing && selfClosing)
			throw new XMLException(this, "double slashes at element: " + s);
		
		// remove opening and closing slashes
		if (closing) elementStr = elementStr.substring(1);
		if (selfClosing) elementStr = elementStr.substring(0, elementStr.length() - 1);
		
		// parse tag element name with namespace
		Matcher elementName = ELEMENT_NAME.matcher(elementStr);
		if (!elementName.find())
			throw new XMLException(this, "invalid element name: " + s);
		
		// test for excess characters before brackets on closing elements
		if (closing && elementName.end() != elementStr.length())
			throw new XMLException(this, "closing element name slash has to follow immediately: " + s);
		
		Map<String, URI> namespaces = this.namespaces;
		
		// open or close new element tag
		if (closing)
			closeTag(elementName.group());
		else if (!selfClosing)
			openTag(elementName.group());
		
		// decide whether to use previous namespace map (closing tags) new namespace map (open tag) or temporary namespace map (self closing tags)
		if (!closing) namespaces = this.namespaces;
		if (selfClosing) namespaces = new HashMap<String, URI>(this.namespaces);
		
		// parse attributes if not a closing tag
		Map<String, String> attributeMap = new HashMap<String, String>();
		if (!closing) {
			String attributeStr = elementStr.substring(elementName.end());
			Matcher attributes = ATTRIBUTE.matcher(attributeStr);
			int last = 0;
			while (attributes.find()) {
				last = attributes.end();
				String attributeName = attributes.group(1);
				String valueStr = attributes.group(2);
				if (valueStr == null) valueStr = attributes.group(3);
				
				// check for namespace declaration
				Matcher xmlns = NAMESPACE.matcher(attributeName);
				if (xmlns.find()) {
					try {
						namespaces.put(xmlns.group(1) == null ? "" : xmlns.group(1), new URI(valueStr));
						continue;
					} catch (URISyntaxException e) {
						throw new XMLException(this, "malformed XML namespace URI", e);
					}
				}
				
				attributeMap.put(attributeName, valueStr);
			}
			
			if (last != attributeStr.length())
				throw new XMLException(this, "excess characters after attributes: " + s);
		} else {
			if (elementName.end() != elementStr.length())
				throw new XMLException(this, "excess characters after element name: " + s);
		}
		
		// construct element descriptor
		return new ElementDescriptor(
				selfClosing ? DescType.SELF_CLOSING : closing ? DescType.CLOSE : DescType.OPEN,
				namespaces.get(elementName.group(1) == null ? "" : elementName.group(1)),
				elementName.group(2),
				closing ? null : attributeMap);
	}
	
	/**
	 * Reads the next tag element from the stream and returns an element descriptor for it.<br>
	 * If there is text data that has to be read before the next element, or there are no more elements, this method will return null.
	 * @return An element descriptor describing the next tag element or null if there are no more elements or text data has to be read first
	 * @throws IOException If an IO exception occurred while accessing the source stream
	 * @throws XMLException If an exception occurred while parsing the XML content
	 */
	public ElementDescriptor readNext() throws IOException, XMLException {
		// do not allow to continue parsing within an CDATA block
		if (cdataParsing) return null;
		
		// do not continue parsing if this stream is an split stream and left its starting element
		if (isSplit && stack.isEmpty()) return null;
		
		if (this.version == null || this.encoding == null)
			readProlog();
		
		try {

			// skip all white spaces
			int w = 0; while (Character.isWhitespace(readAt(w))) w++;
			deleteN(w);
			
		} catch (EOFException e) {
			// if we are outside the root element, an EOF indicates the end of the file
			if (this.stack.isEmpty()) return null;
			throw e;
		}
		
		// check for CDATA block
		if (readN(9).equals("<![CDATA[")) return null;
		
		// check for comment block and skip
		if (readN(4).equals("<!--")) {
			int s = 6;
			while (!readN(s).endsWith("-->")) s++;
			deleteN(s);
		}
		
		if (readAt(0) == '<') {
			int i = findFirst('>') + 1;
			String elementStr = readN(i).substring(1, i - 1);
			deleteN(i);
			
			return parseElementString(elementStr);
		}
		
		// Text data within element
		return null;
	}

	/** if the parser is currently parsing an CDATA block */
	private boolean cdataParsing = false;
	
	/**
	 * Reads text data from within the currently open element.<br>
	 * All available text has to be read before the next element can be read.<br>
	 * NOTE: Leading white spaces outside of CDATA blocks, including new-line's will be discarded.
	 * @param cbuf The character buffer to read the text data to
	 * @param off The offset in the buffer to start putting the data
	 * @param len The length of the text data to read
	 * @return The number of characters actually read or -1 if the end of the text was reached
	 * @throws IOException If an IO exception occurred while accessing the source stream
	 */
	public int readText(char[] cbuf, int off, int len) throws IOException {
		Objects.requireNonNull(cbuf, "character buffer can not be null");
		if ((off < 0) || (off > cbuf.length) || (len < 0) ||
			((off + len) > cbuf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}

		int p = 0;
		
		try {

			// check if char buffer full or and of text reached, if not, continue
			while (p < len && (cdataParsing || readAt(0) != '<')) {

				// find start of next tag or CDATA block or end of CDATA block
				int i = 0;
				if (!cdataParsing) {
					while (readAt(i) != '<' && i < len) i++;
				} else {
					// make sure to read 2 more characters than len, to prevent a cut of end sequence from being interpreted as text
					while (!readN(i + 3).endsWith("]]>") && i < len + 2) i++;
				}
				
				// copy text up to that to fill up char buffer
				String text = readN(i);
				if (!cdataParsing) {
					// if not parsing CDATA block, replace character codes
					text = text.replaceAll("&lt;", "<");
					text = text.replaceAll("&gt;", ">");
					text = text.replaceAll("&amp;", "&");
					text = text.replaceAll("&apos;", "'");
					text = text.replaceAll("&quot;", "\"");
				}
				text.getChars(0, Math.min(len, text.length()), cbuf, off + p);
				deleteN(i);
				p += text.length();

				// check for comment block and skip
				if (!cdataParsing && readN(4).equals("<!--")) {
					int s = 6;
					while (!readN(s).endsWith("-->")) s++;
					deleteN(s);
				}
				
				// check if start or end of CDATA block
				if (!cdataParsing) {
					if (i <= len && readN(9).equals("<![CDATA[")) {
						deleteN(9);
						cdataParsing = true;
					}
				} else {
					if (i <= len && readN(3).equals("]]>")) {
						deleteN(3);
						cdataParsing = false;
					}
				}
				
			}
			
			return p;
			
		} catch (EOFException e) {
			// if we are outside the root element, an EOF indicates the end of the file
			if (this.stack.isEmpty()) return p == 0 ? -1 : p;
			throw e;
		}
		
	}
	
	/**
	 * Reads all text data available from within the currently open element.<br>
	 * All available text has to be read before the next element can be read.<br>
	 * NOTE: Leading white spaces outside of CDATA blocks, including new-line's will be discarded.
	 * @return The text data read or null if no data was available
	 * @throws IOException If an IO exception occurred while accessing the source stream
	 */
	public String readAllText() throws IOException {
		StringBuffer buffer = new StringBuffer();
		char[] buf = new char[2048];
		int r = 0;
		while ((r = readText(buf, 0, 2048)) > 0)
			buffer.append(buf, 0, r);
		return r == -1 ? null : buffer.toString();
	}
	
	@Override
	public String xmlStackPath() {
		return this.stack.stream().map(TagEntry::name).reduce((a, b) -> a + "." + b).orElse("");
	}
	
	/**
	 * Splits a new XMLInputStream which reads from the same source as this stream, but treats the and of the currently open XML element as an EOF.<br>
	 * Basically, this stream only allows to read what is remaining in the currently opened XML element and nothing more.<br>
	 * Should the split stream be discarded (no longer used) before reaching EOF, this stream continues to read where it left.<br>
	 * Reading from both streams before the split stream reached EOF or is discarded of results in undefined behavior.<br>
	 * @return The split stream, or null if this stream already reached EOF
	 */
	public XMLInputStream splitStream() {
		return new XMLInputStream(this);
	}
	
	/**
	 * Indicates that this stream was split from an parent stream by the {@link XMLInputStream#splitStream()} method.
	 * @return true if this is a split stream
	 */
	public boolean isSplit() {
		return isSplit;
	}
	
}
