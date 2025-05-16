package de.m_marvin.basicxml.marshalling.internal;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.print.DocFlavor.URL;

public class NamespaceMap<T> {
	
	private final boolean ignoreNamespaces;
	private final HashMap<String, Map<String, T>> map;
	
	public NamespaceMap(boolean ignoreNamespace) {
		this.ignoreNamespaces = ignoreNamespace;
		this.map = new HashMap<String, Map<String,T>>();
	}

	public T get(URI namespace, String name) {
		return get(namespace == null ? null : namespace.toString(), name);
	}
	
	public T get(String namespace, String name) {
		Map<String, T> m = this.map.get(this.ignoreNamespaces ? null : namespace);
		if (m == null) return null;
		return m.get(name);
	}

	public T put(URI namespace, String name, T value) {
		return put(namespace == null ? null : namespace.toString(), name, value);
	}
	
	public T put(String namespace, String name, T value) {
		Map<String, T> m = this.map.get(this.ignoreNamespaces ? null : namespace);
		if (m == null) {
			m = new HashMap<String, T>();
			this.map.put(this.ignoreNamespaces ? null : namespace, m);
		}
		return m.put(name, value);
	}

	public T remove(URL namespace, String name) {
		return remove(namespace == null ? null : namespace.toString(), name);
	}
	
	public T remove(String namespace, String name) {
		Map<String, T> m = this.map.get(this.ignoreNamespaces ? null : namespace);
		if (m == null) return null;
		T r = m.remove(name);
		if (m.isEmpty()) this.map.remove(this.ignoreNamespaces ? null : namespace);
		return r;
	}
	
	public void clear() {
		this.map.clear();
	}
	
}
