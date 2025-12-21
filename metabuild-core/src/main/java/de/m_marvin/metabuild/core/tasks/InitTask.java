package de.m_marvin.metabuild.core.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.api.core.IMeta;
import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.util.FileUtility;

public class InitTask extends BuildTask {

	private static final File WRAPPER_LOCATION = new File("meta/metabuild-wrapper.jar");
	private static final File METAW_LOCATION_WIN = new File("metaw.cmd");
	private static final File METAW_LOCATION_LIN = new File("metaw");

	private static final String WRAPPER_INCLUDE_LOC = "wrapper/wrapper.zip";
	private static final String METAW_INCLUDE_LOC_WIN = "wrapper/metaw.cmd";
	private static final String METAW_INCLUDE_LOC_LIN = "wrapper/metaw";
	
	public InitTask(String name) {
		super(name);
	}
	
	@Override
	protected TaskState prepare() {
		
		// only run if no wrapper was found, aka no project is setup yet
		File wrapperJar = FileUtility.absolute(WRAPPER_LOCATION);
		return wrapperJar.isFile() ? TaskState.UPTODATE : TaskState.OUTDATED;
		
	}
	
	@Override
	protected boolean run() {
		
		logger().warn("! RUNNING METABUILD INIT SEQUENCE, REWRITING WRAPPER JAR");
		logger().warn("! METAW SCRIPTS WILL BE REWRITTEN");
		
		File wrapperJar = FileUtility.absolute(WRAPPER_LOCATION);
		File metawWin = FileUtility.absolute(METAW_LOCATION_WIN);
		File metawLin = FileUtility.absolute(METAW_LOCATION_LIN);
		
		try {
			writeWrapper(wrapperJar);
			writeFile(metawWin, METAW_INCLUDE_LOC_WIN);
			writeFile(metawLin, METAW_INCLUDE_LOC_LIN);
			return true;
		} catch (IOException e) {
			throw BuildException.msg(e, "unable to rewrite write wrapper jar, NOTE: do not rewrite wrapper using 'metaw init -force', use 'meta init -force' instead!");
		}
		
	}

	public void writeFile(File file, String resource) throws IOException {
		InputStream rstream = this.getClass().getClassLoader().getResourceAsStream(resource);
		if (rstream == null)
			throw new IOException("resource unavailable: " + resource);
		try {
			file.getParentFile().mkdirs();
			FileOutputStream fstream = new FileOutputStream(file);
			rstream.transferTo(fstream);
			fstream.close();
			rstream.close();
		} catch (IOException e) {
			throw new IOException("unable to write file: " + file, e);
		}
	}
	
	public void writeWrapper(File file) throws IOException {
		IMeta mb = Metabuild.get();
		InputStream wrapperJar = this.getClass().getClassLoader().getResourceAsStream(WRAPPER_INCLUDE_LOC);
		if (wrapperJar == null)
			throw new IOException("resource unavailable: wrapper jar");
		try {
			file.getParentFile().mkdirs();
			ZipInputStream included = new ZipInputStream(wrapperJar);
			ZipOutputStream wrapper = new ZipOutputStream(new FileOutputStream(file));
			
			// write included wrapper files
			ZipEntry entry;
			while ((entry = included.getNextEntry()) != null) {
				wrapper.putNextEntry(entry);
				included.transferTo(wrapper);
				// write default version entry in manifest
				if (entry.getName().equals("META-INF/MANIFEST.MF"))
					wrapper.write(("Meta-Version: " + mb.getMetabuildVersion() + "\n").getBytes(StandardCharsets.UTF_8));
				wrapper.closeEntry();
				included.closeEntry();
			}
			included.close();
			
			// write installation files
			wrapper.putNextEntry(new ZipEntry("included/metabuild-" + mb.getMetabuildVersion() + ".zip"));
			ZipOutputStream install = new ZipOutputStream(wrapper); 
			for (File f : FileUtility.deepList(mb.metaHome())) {
				if (f.equals(file)) continue; // this is to prevent an rare edge case during debugging
				install.putNextEntry(new ZipEntry(FileUtility.relative(f, mb.metaHome()).toString().replace('\\', '/')));
				InputStream fstream = new FileInputStream(f);
				fstream.transferTo(install);
				fstream.close();	
				install.closeEntry();
			}
			install.finish();
			install.flush();
			wrapper.closeEntry();
			
			// write meta_versions.cfg file
			wrapper.putNextEntry(new ZipEntry("meta_versions.cfg"));
			String cfgEntry = String.format("%s = jar:${mwjar}!/included/metabuild-%s.zip", mb.getMetabuildVersion(), mb.getMetabuildVersion());
			wrapper.write(cfgEntry.getBytes(StandardCharsets.UTF_8));
			wrapper.closeEntry();
			wrapper.close();
		} catch (IOException e) {
			throw new IOException("failed to write wrapper jar", e);
		}
	}
	
}
