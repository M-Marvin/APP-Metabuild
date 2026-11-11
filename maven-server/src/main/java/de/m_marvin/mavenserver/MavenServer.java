package de.m_marvin.mavenserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.m_marvin.cliutil.arguments.Arguments;
import de.m_marvin.cliutil.arguments.CommandArgumentParser;
import de.m_marvin.cliutil.exception.CommandArgumentException;
import de.m_marvin.http.HttpCode;
import de.m_marvin.http.PathInfo;
import de.m_marvin.http.ResponseInfo;
import de.m_marvin.http.server.HttpServer;
import de.m_marvin.http.server.HttpsServer;
import de.m_marvin.mavenserver.types.ArtifactLocation;
import de.m_marvin.mavenserver.types.MavenDatabaseException;
import de.m_marvin.simplelogging.Log;
import de.m_marvin.simplelogging.impl.MultiLogger;
import de.m_marvin.simplelogging.impl.StacktraceLogger;
import de.m_marvin.simplelogging.impl.StreamLogger;
import de.m_marvin.simplelogging.impl.SynchronizedLogger;
import de.m_marvin.simplelogging.impl.SystemLogger;
import de.m_marvin.simplelogging.misc.LogFileProvider;
import de.m_marvin.stdextras.io.LimitedInputStream;

public class MavenServer {
	
	public static void main(String[] args) {
		
		ArtifactLocation.fromPath("de/m_marvin/commandlineparser/commandlineutility/2.2.3/commandlineutility-2.2.3.jar");
		
		CommandArgumentParser parser = new CommandArgumentParser();
		parser.addOption("help", false, "print this help info");
		parser.addOption("repo", new File(System.getProperty("user.home"), ".maven"), "maven repository directory");
		parser.addOption("snapage", 1440, "time to live for a snapshot after being uploaded, <= 0 means indefinetly");
		parser.addOption("port", -1, "http server port number, < 0 to disable");
		parser.addOption("addr", (String) null, "http server ip address");
		parser.addOption("sport", -1, "https server port number, < 0 to disable");
		parser.addOption("saddr", (String) null, "https server ip address");
		parser.addOption("logs", new File("./logs"), "log folder location");
		
		try {
			
			Arguments arguments = parser.parse(args);
			
			if (arguments.flag("help") || args.length == 0) {
				System.out.println(parser.printHelp());
				System.exit(1);
			}
			
			
			File logFolder = arguments.get("logs");
			if (!logFolder.isDirectory() && !logFolder.mkdir()) {
				System.err.println("unable to create log folder: " + logFolder);
				System.exit(1);
			}
			
			LogFileProvider logProvider = new LogFileProvider(logFolder);
			Log.setDefaultLogger(new SynchronizedLogger(new StacktraceLogger(new MultiLogger(new SystemLogger(true), new StreamLogger(logProvider.beginLogging())))));

			MavenServer server = new MavenServer(arguments.get("repo"), arguments.get("snapage"));
			Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
			
			if ((int) arguments.get("port") >= 0) {
				try {
					server.configureHttp(arguments.get("addr") == null ? null : InetAddress.getByName(arguments.get("addr")), arguments.get("port"));
				} catch (UnknownHostException e) {
					Log.defaultLogger().errort("startup", "http socket address malformed: %s (%d)", arguments.get("addr"), arguments.get("port"), e);
				}
			}
			
			if ((int) arguments.get("sport") >= 0) {
				try {
					server.configureHttps(arguments.get("saddr") == null ? null : InetAddress.getByName(arguments.get("saddr")), arguments.get("sport"));
				} catch (UnknownHostException e) {
					Log.defaultLogger().errort("startup", "https socket address malformed: %s (%d)", arguments.get("saddr"), arguments.get("sport"), e);
				}
			}
			
			server.run();
			
			logProvider.endLogging();
			
		} catch (CommandArgumentException | IOException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	private ExecutorService mavenClientPool = Executors.newCachedThreadPool();
	private HttpsServer httpsServer;
	private HttpServer httpServer;
	
	private volatile Boolean shouldTerminate = false;
	private Thread serverThread;
	
	private MavenDatabase database;
	
	public MavenServer(File mavenRepository, int snapshotTimeToLive) throws IOException {
		Log.defaultLogger().infot("startup", "repository database location: %s", mavenRepository);
		Log.defaultLogger().infot("startup", "repository snapshot time to live: %d sec", snapshotTimeToLive);
		this.database = new MavenDatabase(mavenRepository, snapshotTimeToLive);
	}
	
	public void configureHttp(InetAddress address, int port) {
		Log.defaultLogger().infot("startup", "http socket: %s port: %d", address, port);
		this.httpServer = new HttpServer(port, address, HttpServer.DEFAULT_BACKLOG, HttpServer.DEFAULT_TIMEOUT, this.mavenClientPool);
		this.httpServer.setPutHandler(this::handlePut);
		this.httpServer.setGetHandler(this::handleGet);
	}
	
	public void configureHttps(InetAddress address, int port) {
		Log.defaultLogger().infot("startup", "https socket: %s port: %d", address, port);
		this.httpsServer = new HttpsServer(port, address, HttpsServer.DEFAULT_BACKLOG, HttpsServer.DEFAULT_TIMEOUT, this.mavenClientPool);
		this.httpsServer.setPutHandler(this::handlePut);
		this.httpsServer.setGetHandler(this::handleGet);
	}
	
	public void shutdown() {
		this.shouldTerminate = true;
		if (this.serverThread != Thread.currentThread()) {
			try {
				this.serverThread.interrupt();
				this.serverThread.join();
			} catch (InterruptedException e) {}
		}
	}
	
	public void run() {
		this.serverThread = Thread.currentThread();

		Log.defaultLogger().infot("startup", "server starting ...");
		
		try {
			if (this.httpServer != null)
				this.httpServer.open();
			if (this.httpsServer != null)
				this.httpsServer.open();
		} catch (IOException e) {
			Log.defaultLogger().errort("server", "unable to open server server:", e);
			shutdown();
		}
		
		while (!this.shouldTerminate) {
			try {
				this.database.cleanDatabase();
			} catch (Exception e) {
				Log.defaultLogger().warnt("server/periodic", "exception in periodic server actions: ", e);
			} catch (Error e) {
				Log.defaultLogger().errort("server/periodic", "error in periodic server actions: ", e);
				shutdown();
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {}
		}
		
		Log.defaultLogger().infot("shutdown", "shuting down ...");

		try {
				
			this.mavenClientPool.shutdown();
			this.mavenClientPool.awaitTermination(20, TimeUnit.SECONDS);
			
			if (this.httpServer != null)
				this.httpServer.close();
			if (this.httpsServer != null)
				this.httpsServer.close();
			
			Log.defaultLogger().infot("shutdown", "terminated");
			
		} catch (IOException | InterruptedException e) {
			Log.defaultLogger().errort("shutdown", "server process shutdown error:", e);
		}
		
	}

	public ResponseInfo handleGet(PathInfo path, Map<String, String> attributes) {
		
		try {
			InputStream downloadStream;
			if (MavenDatabase.METADATA_PATTERN.matcher(path.getPath()).find()) {
				downloadStream = this.database.downloadMetadata(path.getPath().substring(1));
			} else {
				ArtifactLocation artifact = ArtifactLocation.fromPath(path.getPath().substring(1));
				downloadStream = this.database.downloadArtifact(artifact);
			}
			if (downloadStream == null)
				return new ResponseInfo(HttpCode.NOT_FOUND, "not found", null);
			return new ResponseInfo(HttpCode.OK, "ok", downloadStream);
		} catch (MavenDatabaseException | IOException e) {
			Log.defaultLogger().warnt("database/download", "an exception occured while download: " + path.getPath(), e);
			return new ResponseInfo(HttpCode.INTERNAL_SERVER_ERROR, "database error", null);
		} catch (IllegalArgumentException e) {
			Log.defaultLogger().warnt("database/download", "attempted download with invalid artifact location: %s", path.getPath(), e);
			return new ResponseInfo(HttpCode.BAD_REQUEST, "invalid artifact location", null);
		}
		
	}
	
	public ResponseInfo handlePut(PathInfo path, Map<String, String> attributes, int contentLength, InputStream contentStream) {
		
		try {
			contentStream = new LimitedInputStream(contentStream, contentLength, false);
			if (MavenDatabase.METADATA_PATTERN.matcher(path.getPath()).find()) {
				this.database.uploadMetadata(path.getPath().substring(1), contentStream);
			} else {
				ArtifactLocation artifact = ArtifactLocation.fromPath(path.getPath().substring(1));
				OutputStream uploadStream = this.database.uploadArtifact(artifact);
				contentStream.transferTo(uploadStream);
				uploadStream.close();
			}
			return new ResponseInfo(HttpCode.OK, "ok", null);
		} catch (MavenDatabaseException | IOException e) {
			Log.defaultLogger().warnt("database/upload", "an exception occured while uploading: " + path.getPath(), e);
			return new ResponseInfo(HttpCode.INTERNAL_SERVER_ERROR, "database error", null);
		} catch (IllegalArgumentException e) {
			Log.defaultLogger().warnt("database/upload", "attempted upload with invalid artifact location: %s", path.getPath(), e);
			return new ResponseInfo(HttpCode.BAD_REQUEST, "invalid artifact location", null);
		}
		
	}
	
}
