package de.m_marvin.basicxml.marshaling.adapter;

/**
 * An type adapter for converting between class field data type and XML data, there exist two versions of this interface, this one is only the super type of both
 * @param <V> The value type of the field
 * @param <P> The type of the parent class in which the fields data type class is defined, used when construction non-static classes
 */
public interface XMLClassFieldAdapter<V, P> {

	public V adaptType(String str, P parentObject);
	
	public String typeString(V value);
	
}
