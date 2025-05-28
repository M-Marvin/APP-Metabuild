package de.m_marvin.metabuild.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HashUtility {
	
	private static MessageDigest hasher;
	
	static {
		try {
			hasher = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Hashing algorithm MD5 unavailable");
		}
	}
	
	public static String hash(String text) {
		return hash(text.getBytes(StandardCharsets.UTF_8));
	}

	public static String hash(byte[] data) {
		return HexFormat.of().formatHex(hasher.digest(data));
	}
	
}
