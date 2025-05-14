package de.m_marvin.basicxml.marshalling;

public interface XMLClassFieldAdapter<V, P> {
	
	public V adaptType(String str, P parentObject);
	
	public String typeString(V value);
	
}
