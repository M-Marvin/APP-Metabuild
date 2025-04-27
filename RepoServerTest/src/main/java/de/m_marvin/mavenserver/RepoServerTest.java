package de.m_marvin.mavenserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import de.m_marvin.http.HttpCode;
import de.m_marvin.http.PathInfo;
import de.m_marvin.http.ResponseInfo;
import de.m_marvin.http.server.HttpServer;
import de.m_marvin.simplelogging.Log;

public class RepoServerTest {
	
	private static File files;
	
	public static void main(String... args) throws MalformedURLException, URISyntaxException {
		
		HttpServer httpServer = new HttpServer(80);
		
		httpServer.setGetHandler(RepoServerTest::handleGetRequest);
		httpServer.setPutHandler(RepoServerTest::handlePutRequest);
		httpServer.setPostHandler(RepoServerTest::handlePostRequest);
		httpServer.setDeleteHandler(RepoServerTest::handleDeleteRequest);
		
		files = new File(RepoServerTest.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL().getPath(), "../../files");
		
		Log.defaultLogger().info("files location: %s", files);
		
		try {
			
			httpServer.open();
			Log.defaultLogger().info("https server running");
			
			while (true) try { Thread.sleep(60000); } catch (InterruptedException e) {}
			
//			httpServer.close();
			
		} catch (IOException e) {
			Log.defaultLogger().error("failed to open http server: ", e);
			System.exit(-1);
		}
		
		System.exit(0);
		
	}
	
	public static  ResponseInfo handleGetRequest(PathInfo path, Map<String, String> attributes) {
		try {
			InputStream fileStream = new FileInputStream(new File(files, path.getPath()));
			return new ResponseInfo(HttpCode.OK, "OK", fileStream);
		} catch (FileNotFoundException e) {
			return new ResponseInfo(HttpCode.NOT_FOUND, "File Not Found", null);
		}
	}
	
	public static ResponseInfo handlePutRequest(PathInfo path, Map<String, String> attributes, int contentLength, InputStream contentStream) {
		try {
			File file = new File(files, path.getPath());
			file.getParentFile().mkdirs();
			OutputStream fileStream = new FileOutputStream(file);
			fileStream.write(contentStream.readNBytes(contentLength));
			fileStream.close();
			return new ResponseInfo(HttpCode.OK, "File Written", null);
		} catch (IOException e) {
			return new ResponseInfo(HttpCode.INTERNAL_SERVER_ERROR, "IO Error", null);
		}
	}

	public static ResponseInfo handlePostRequest(PathInfo path, Map<String, String> attributes, int contentLength, InputStream contentStream) {
		System.out.println("POST -> " + path);
		// TODO Auto-generated method stub
		return null;
	}

	public static ResponseInfo handleDeleteRequest(PathInfo path, Map<String, String> attributes) {
		System.out.println("DEL -> " + path);
		// TODO Auto-generated method stub
		return null;
	}
	
}
