package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.marshaling.XMLMarshaler;
import de.m_marvin.basicxml.marshaling.XMLUnmarshaler;

public class Test {
	
	public static void main(String... args) throws Exception, URISyntaxException {
		
		File dir = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getPath(), "../../");
		
		OutputStream output = new FileOutputStream(new File(dir, "/test/test2.xml"));
		
		XMLOutputStream xmlOut = new XMLOutputStream(output);
		
		InputStream input = new FileInputStream(new File(dir, "/test/test.xml"));
		
		XMLInputStream xmlIn = new XMLInputStream(input);
		
//		String text;
//		ElementDescriptor element;
//		do {
//			element = xmlIn.readNext();
//			xmlOut.writeNext(element);
//			text = xmlIn.readAllText();
//			if (text != null && !text.isBlank())
//				xmlOut.writeAllText(text, false);
//		} while (text != null);
//		
//		xmlIn.close();
//		xmlOut.close();
		
		XMLUnmarshaler unmarshaller = new XMLUnmarshaler(true, TestType.class);
		
		var object = unmarshaller.unmarshall(xmlIn, TestType.class);
		
		System.out.println(object.testlist.testitem.get(0).value);
		
		for (String k : object.remaining.keySet()) {
			System.out.println(k + " = " + object.remaining.get(k));
		}
		
		System.out.println(object.zzz);
		
		XMLMarshaler marshaler = new XMLMarshaler(false, TestType.class);
		
		marshaler.marshal(xmlOut, object);
		
//		System.out.println("Version: " + xmlIn.getVersion());
//		System.out.println("Encoding: " + xmlIn.getEncoding());
//		
//		for (int i = 0; i < 100; i++) {
//			var element = xmlIn.readNext();
//			if (element == null) {
//				String text = xmlIn.readAllText();
//				if (text == null) break;
//				System.out.println(text);
//			} else {
//				System.out.println(element);
//			}
//		}
//		
//		xmlIn.close();
		
	}
	
}
