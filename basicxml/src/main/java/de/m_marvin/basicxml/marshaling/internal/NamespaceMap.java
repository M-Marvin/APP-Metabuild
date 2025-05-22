package de.m_marvin.basicxml.marshaling.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.print.DocFlavor.URL;

public class NamespaceMap<T> {
	
	private final boolean ignoreNamespaces;
	private final Map<URI, Map<String, T>> map;
	
	public NamespaceMap(boolean ignoreNamespace) {
		this.ignoreNamespaces = ignoreNamespace;
		this.map = new LinkedHashMap<URI, Map<String,T>>();
	}

	public T get(String namespace, String name) {
		try {
			return get(namespace == null ? null : new URI(namespace), name);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("supplied namespace is not a valid URI", e);
		}
	}
	
	public T get(URI namespace, String name) {
		Map<String, T> m = this.map.get(this.ignoreNamespaces ? null : namespace);
		if (m == null) return null;
		return m.get(name);
	}

	public T put(String namespace, String name, T value) {
		try {
			return put(namespace == null ? null : new URI(namespace), name, value);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("supplied namespace is not a valid URI", e);
		}
	}
	
	public T put(URI namespace, String name, T value) {
		Map<String, T> m = this.map.get(this.ignoreNamespaces ? null : namespace);
		if (m == null) {
			m = new LinkedHashMap<String, T>();
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

	public static record Entry<T>(URI namespace, String name, T value) {}
	
	public List<Entry<T>> entrySet() {
		return this.map.keySet().stream().flatMap(n -> this.map.get(n).entrySet().stream().map(e -> new Entry<T>(n, e.getKey(), e.getValue()))).toList();
	}
	
	public static record Key(URI namespace, String name) {}
	
	public List<Key> keySet() {
		return this.map.keySet().stream().flatMap(n -> this.map.get(n).keySet().stream().map(e -> new Key(n, e))).toList();
	}

	public List<T> valueSet() {
		return this.map.values().stream().flatMap(m -> m.values().stream()).toList();
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append('{');
		boolean b = true;
		for (var e : entrySet()) {
			if (b)
				b = false;
			else
				buf.append(", ");
			buf.append('[').append(e.namespace()).append(',').append(e.name()).append(']');
			buf.append('=');
			buf.append(e.value().toString());
		}
		buf.append('}');
		return buf.toString();
	}
	
	@Override
	public int hashCode() {
		return this.map.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof NamespaceMap other)
			return other.map.equals(this.map);
		return false;
	}
	
}
