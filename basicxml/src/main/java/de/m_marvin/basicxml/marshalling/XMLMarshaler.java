package de.m_marvin.basicxml.marshalling;

import java.util.HashMap;
import java.util.Map;

import de.m_marvin.basicxml.marshalling.internal.XMLClassType;

public class XMLMarshaler {

	private final Map<Class<?>, XMLClassType<?, ?>> types = new HashMap<>();
	
	public XMLMarshaler(boolean ignoreNamespaces, Class<?>... types) {
		for (Class<?> type : types) {
			resolveTypeObjects(type, null, ignoreNamespaces);
		}
	}
	
	private void resolveTypeObjects(Class<?> type, Class<?> parent, boolean ignoreNamespace) {
		var typeObj = XMLClassType.makeFromClass(type, parent, ignoreNamespace);
		this.types.put(type, typeObj);
		for (Class<?> subTypes : typeObj.subTypes()) {
			resolveTypeObjects(subTypes, type, ignoreNamespace);
		}
	}
	
	public <T> void marshal(T object) {
		
	}
	
}
