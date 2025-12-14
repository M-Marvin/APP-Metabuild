package de.m_marvin.metabuild.maven.types;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Supplier;

import de.m_marvin.metabuild.core.util.HashUtility;
import de.m_marvin.metabuild.maven.exception.MavenException;
import de.m_marvin.metabuild.maven.types.Artifact.DataLevel;

public class Repository {

	public final String name;
	public final URL baseURL;
	public final Credentials credentials;
	public final boolean isLocal;

	public Repository(String name, String baseURL) {
		this(name, baseURL, null);
	}

	public Repository(String name, String baseURL, Credentials credentials) {
		this(name, baseURL, credentials, false);
	}
	
	public Repository(String name, String baseURL, Credentials credentials, boolean isLocal) {
		try {
			this.name = name;
			this.baseURL = new URI(baseURL.replace('\\', '/')).toURL();
			this.credentials = credentials;
			this.isLocal = isLocal;
		} catch (MalformedURLException | URISyntaxException e) {
			throw new IllegalArgumentException("invalid repository URL", e);
		}
	}
	
	public Repository(String name, URL baseURL) {
		this(name, baseURL, null);
	}

	public Repository(String name, URL baseURL, Credentials credentials) {
		this(name, baseURL, credentials, false);
	}
	
	public Repository(String name, URL baseURL, Credentials credentials, boolean isLocal) {
		this.name = name;
		this.baseURL = baseURL;
		this.credentials = credentials;
		this.isLocal = isLocal;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(this.baseURL);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Repository other) {
			return Objects.equals(this.baseURL, other.baseURL);
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "Repositiry{url=" + this.baseURL + ",credentials=" + (this.credentials != null ? "yes" : "no") + "}";
	}
	
	public String getCacheFolder() {
		return String.format("rrp_%s", HashUtility.hash(this.baseURL.toString()));
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
					String username = Credentials.this.username.get();
					String password = Credentials.this.password.get();
					return new PasswordAuthentication(username == null ? "" : username, (password == null ? "" : password).toCharArray());
				}
			};
		}
		
		public String bearer() {
			return this.token.get();
		}
		
	}
	
	public URL artifactURL(Artifact artifact, DataLevel dataLevel, ArtifactFile artifactFile) throws MavenException {
		try {
			return new URI(this.baseURL.getProtocol(), null, this.baseURL.getHost(), this.baseURL.getPort(), this.baseURL.getFile() + artifact.getLocalPath(dataLevel) + artifactFile.getExtension(), null, null).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
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
