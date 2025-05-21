package de.m_marvin.basicxml.marshalling;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLOutputStream;
import de.m_marvin.basicxml.XMLStream.DescType;
import de.m_marvin.basicxml.XMLStream.ElementDescriptor;
import de.m_marvin.basicxml.marshalling.annotations.XMLCDATA;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLRootType;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.basicxml.marshalling.internal.NamespaceMap;
import de.m_marvin.basicxml.marshalling.internal.NamespaceMap.Entry;
import de.m_marvin.basicxml.marshalling.internal.NamespaceMap.Key;
import de.m_marvin.basicxml.marshalling.internal.XMLClassField;
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
	
	public <T> void marshal(XMLOutputStream xmlStream, T object) throws XMLMarshalingException, IOException, XMLException {
		
		if (!object.getClass().isAnnotationPresent(XMLType.class))
			throw new XMLMarshalingException("class is not annotated as XML type");
		
		XMLRootType xmlRootAnnotation = object.getClass().getAnnotation(XMLRootType.class);
		if (xmlRootAnnotation == null)
			throw new XMLMarshalingException("class is not annotated as XML root type");
		
		URI namespace = null;
		if (!xmlRootAnnotation.namespace().equals(XMLField.NULL_STR))
			try {
				namespace = new URI(xmlRootAnnotation.namespace());
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("supplied namspace is not a vald URI: " + xmlRootAnnotation.namespace(), e);
			}
		
		writeElementObject(xmlStream, namespace, xmlRootAnnotation.value(), object);
		
		xmlStream.close();
		
	}
	
	protected <T, P> void writeElement(XMLOutputStream xmlStream, URI namespace, String name, XMLClassField<T, P> xmlField, Object xmlClassObject) throws XMLMarshalingException, IOException, XMLException {
		
		T[] value = xmlField.query(xmlClassObject, name);
		
		if (xmlField.adapter() != null || xmlField.isPrimitive()) {
			for (T elementValue : value) {
				String elementValueStr = xmlField.adapter() != null ? 
						xmlField.adapter().typeString(elementValue) : 
						XMLClassField.primitiveString(xmlField.type(), elementValue);
				if (elementValueStr == null) continue;
				
				// write element text data
				ElementDescriptor openingElement = new ElementDescriptor(DescType.OPEN, namespace, name, null);
				xmlStream.writeNext(openingElement);
				boolean useCData = xmlField.field().isAnnotationPresent(XMLCDATA.class);
				xmlStream.writeAllText(elementValueStr, useCData);
				ElementDescriptor closingElement = new ElementDescriptor(DescType.CLOSE, namespace, name, null);
				xmlStream.writeNext(closingElement);
			}
		} else {
			for (T elementValue : value) {
				// write element
				writeElementObject(xmlStream, namespace, name, elementValue);
			}
		}
		
	}
	
	protected <T, V> void writeElementObject(XMLOutputStream xmlStream, URI namespace, String name, T xmlObject) throws XMLMarshalingException, IOException, XMLException {
		
		@SuppressWarnings("unchecked")
		XMLClassType<T, ?> type = (XMLClassType<T, ?>) this.types.get(xmlObject.getClass());
		if (type == null)
			throw new XMLMarshalingException(xmlStream, "object class unknown to marshaler: " + xmlObject.getClass());
		
		// collect and order attributes
		Map<String, XMLClassField<?, ?>> attributeMap = new LinkedHashMap<>();
		for (String attributeName : type.attributeOrder())
			attributeMap.put(attributeName, type.attributes().get(attributeName));
		for (String attributeName : type.attributes().keySet()) {
			if (attributeMap.get(attributeName) != null) continue;
			if (attributeName.equals(XMLClassType.TEXT_VALUE_FIELD)) continue;
			var attributeField = type.attributes().get(attributeName);
			if (attributeName.equals(XMLClassType.REMAINING_MAP_FIELD)) {
				for (String attributeName2 : attributeField.queryKeys(xmlObject))
					attributeMap.put(attributeName2, attributeField);
			} else {
				attributeMap.put(attributeName, attributeField);
			}
		}

		// collect attribute values
		Map<String, String> attributes = new LinkedHashMap<String, String>();
		for (var attribute : attributeMap.entrySet()) {
			@SuppressWarnings("unchecked")
			XMLClassField<V, ?> attributeField = (XMLClassField<V, ?>) attribute.getValue();
			V[] value = attributeField.query(xmlObject, attribute.getKey());
			if (value.length == 0) continue;
			if (value[0] == null) continue;
			String attributeValue;
			if (attributeField.adapter() != null)
				attributeValue = attributeField.adapter().typeString(value[0]);
			else if (attributeField.isPrimitive())
				attributeValue = XMLClassField.primitiveString(attributeField.type(), value[0]);
			else
				throw new XMLMarshalingException(xmlStream, "attribute type needs type adapter: " + attribute.getKey());
			attributes.put(attribute.getKey(), attributeValue);
		}
		
		// check for text data field
		@SuppressWarnings("unchecked")
		XMLClassField<V, ?> textField = (XMLClassField<V, ?>) type.attributes().get(XMLClassType.TEXT_VALUE_FIELD);
		String textData = null;
		boolean useCData = false;
		if (textField != null) {
			useCData = textField.field().isAnnotationPresent(XMLCDATA.class);
			V[] value = textField.query(xmlObject, null);
			if (value.length > 0) {
				if (textField.adapter() != null) {
					textData = textField.adapter().typeString(value[0]);
				} else if (textField.isPrimitive()) {
					textData = XMLClassField.primitiveString(textField.type(), value[0]);
				} else {
					throw new XMLMarshalingException(xmlStream, "text data field requires type adapter");
				}
			}
		}
		
		// collect and order elements
		NamespaceMap<XMLClassField<?, ?>> elementMap = new NamespaceMap<>(false);
		for (String elementName : type.elementOrder())
			for (Entry<XMLClassField<?, ?>> element : type.elements().entrySet()) {
				if (!element.name().equals(elementName)) continue;
				elementMap.put(element.namespace(), element.name(), element.value());
			}
		for (Key elementKey : type.elements().keySet()) {
			if (elementMap.get(elementKey.namespace(), elementKey.name()) != null) continue;
			var elementField = type.elements().get(elementKey.namespace(), elementKey.name());
			if (elementKey.name().equals(XMLClassType.REMAINING_MAP_FIELD)) {
				for (String elementName : elementField.queryKeys(xmlObject))
					elementMap.put(elementKey.namespace(), elementName, elementField);
			} else {
				elementMap.put(elementKey.namespace(), elementKey.name(), elementField);
			}
		}
		
		// create opening element descriptor
		boolean isSelfClosing = textData == null && elementMap.isEmpty();
		ElementDescriptor openingElement = new ElementDescriptor(isSelfClosing ? DescType.SELF_CLOSING : DescType.OPEN, namespace, name, attributes);
		xmlStream.writeNext(openingElement);
		
		if (isSelfClosing) return;
		
		// write text data
		if (textData != null)
			xmlStream.writeAllText(textData, useCData);
		
		// marshal elements
		for (Entry<XMLClassField<?, ?>> element : elementMap.entrySet()) {
			writeElement(xmlStream, element.namespace(), element.name(), element.value(), xmlObject);
		}
		
		// create closing element descriptor
		ElementDescriptor closingElement = new ElementDescriptor(DescType.CLOSE, namespace, name, null);
		xmlStream.writeNext(closingElement);
		
	}
	
}
