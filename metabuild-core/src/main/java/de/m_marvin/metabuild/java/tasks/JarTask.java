package de.m_marvin.metabuild.java.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.metabuild.core.tasks.ZipTask;

public class JarTask extends ZipTask {

	public static final String MANIFEST_LOC = "META-INF/MANIFEST.MF";
	public static final Pattern META_ENTRY_PATTERN = Pattern.compile("[\\w\\-\\d]+");
	
	public final Map<String, String> metainfo = new HashMap<>();
	
	public JarTask(String name) {
		super(name);
		this.metainfo.put("Manifest-Version", "1.0");
	}
	
	protected boolean buildManifest(StringBuffer sbuffer) {
		for (var entry : this.metainfo.entrySet()) {
			if (!META_ENTRY_PATTERN.matcher(entry.getKey()).find()) {
				logger().errort(logTag(), "invalid manifest entry key: %s", entry.getKey());
				return false;
			}
			sbuffer.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
		}
		return true;
	}
	
	protected boolean archiveManifest(ZipOutputStream zstream) throws IOException {
		StringBuffer sbuffer = new StringBuffer();
		if (!buildManifest(sbuffer)) {
			logger().errort(logTag(), "could not write manifest!");
			return false;
		}
		zstream.putNextEntry(new ZipEntry(MANIFEST_LOC));
		zstream.write(sbuffer.toString().getBytes(StandardCharsets.UTF_8));
		return true;
	}
	
	@Override
	protected boolean archiveFiles(ZipOutputStream zstream) {
		try {
			logger().debugt(logTag(), "archive manifest");
			if (!archiveManifest(zstream)) {
				logger().errort(logTag(), "failed to archive manifest!");
				return false;
			}
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to archive manifest!");
		}
		return super.archiveFiles(zstream);
	}
	
}
