package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.marshalling.XMLMarshaler;

public class Test {
	
	public static void main(String... args) throws URISyntaxException, IOException, XMLException {
		
		File dir = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getPath(), "../../");
		
		InputStream input = new FileInputStream(new File(dir, "/test/maven-metadata.xml"));
		
		XMLInputStream xmlIn = new XMLInputStream(input);
		
		XMLMarshaler marshaller = new XMLMarshaler();
		
		var object = marshaller.unmarshall(xmlIn, TestType.TestSubType.class);
		
		System.out.println(object);
		
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
