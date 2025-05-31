package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.util.FileUtility;

public class Test {
	
	public static void main(String... args) throws URISyntaxException, IOException {
		
		File local = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getFile(), "../../");
		
		Collection<File> filepath = FileUtility.loadFilePath(new File(local, "compile.classpath"));
		
		System.out.println(filepath);
		
		System.exit(-1);
		
		System.out.println(local);

		ZipOutputStream zout1 = new ZipOutputStream(new FileOutputStream(new File(local, "zout1.zip")));
		
		zout1.putNextEntry(new ZipEntry("test.txt"));
		zout1.write("Hi".getBytes());
		zout1.closeEntry();
		
		zout1.close();
		
		ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(new File(local, "zout.zip")));
		
//		zout.putNextEntry(new ZipEntry("test.txt"));
//		zout.write("Hi".getBytes());
//		zout.closeEntry();
		
		zout.putNextEntry(new ZipEntry("zout2.zip"));
		ZipOutputStream zout2 = new ZipOutputStream(zout);
		
		zout2.putNextEntry(new ZipEntry("test.txt"));
		zout2.write("Hi".getBytes());
		zout2.closeEntry();
		
		zout2.finish();
		zout.closeEntry();
		
		zout.close();
		
	}
	
}
