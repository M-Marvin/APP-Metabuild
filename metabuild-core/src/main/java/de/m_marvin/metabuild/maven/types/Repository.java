package de.m_marvin.metabuild.maven.types;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.function.Supplier;

public class Repository {

	public final String name;
	public final URL baseURL;
	public final Credentials credentials;
	
	public Repository(String name, URL baseURL) {
		this(name, baseURL, null);
	}
	
	public Repository(String name, URL baseURL, Credentials credentials) {
		this.name = name;
		this.baseURL = baseURL;
		this.credentials = credentials;
	}
	
	public static record Credentials(Supplier<String> username, Supplier<String> password, Supplier<String> token) {
		
		public Credentials(Supplier<String> username, Supplier<String> password) {
			this(username, password, null);
		}

		public Credentials(Supplier<String> token) {
			this(null, null, token);
		}
		
		public Authenticator authenticator() {
			return new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(Credentials.this.username.get(), Credentials.this.password.get().toCharArray());
				}
			};
		}
		
		public String bearer() {
			return this.token.get();
		}
		
	}
	
	public URL artifactURL(Artifact artifact, ArtifactFile file) throws MavenException {
		try {
			return new URL(this.baseURL.getProtocol(), this.baseURL.getHost(), this.baseURL.getPort(), this.baseURL.getFile() + artifact.getLocalPath() + file.getExtension());
		} catch (MalformedURLException e) {
			throw new MavenException("unable to format artifact URL: %s + %s", this.baseURL, artifact);
		}
	}
	
	public static enum ArtifactFile {
		DATA(""),
		MD5(".md5"),
		SHA1(".sha1"),
		SHA256(".sha256"),
		SHA512(".sha512");
		
		private String extension;
		
		private ArtifactFile(String extension) {
			this.extension = extension;
		}
		
		public String getExtension() {
			return extension;
		}
		
		public String getAlgorithm() {
			return this != DATA ? this.extension.substring(1).toUpperCase() : null;
		}
		
		public static ArtifactFile[] checksums() {
			return new ArtifactFile[] { MD5, SHA1, SHA256, SHA512 };
		}
	}
	
}
