package de.m_marvin.basicxml.marshalling;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLStream.DescType;
import de.m_marvin.basicxml.XMLStream.ElementDescriptor;
import de.m_marvin.basicxml.internal.StackList;
import de.m_marvin.basicxml.marshalling.internal.XMLClassField;
import de.m_marvin.basicxml.marshalling.internal.XMLClassType;
import de.m_marvin.simplelogging.Log;

public class XMLUnmarshaler {
	
	private final Map<Class<?>, XMLClassType<?, ?>> types = new HashMap<>();
	
	public XMLUnmarshaler(boolean ignoreNamespaces, Class<?>... types) {
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
	
	public <T> T unmarshall(XMLInputStream xmlStream, Class<T> objectType, URI fallbackNamespace) throws IOException, XMLException, XMLMarshalingException {
		
		if (fallbackNamespace != null)
			xmlStream.getNamespaces().put("", fallbackNamespace);
		ElementDescriptor element = xmlStream.readNext();
		if (element == null) return null;
		T xmlObject = makeObjectFromXML(xmlStream, element, objectType, new StackList<Object>());
		xmlStream.close();
		return xmlObject;
		
	}
	
	public <T> T unmarshall(XMLInputStream xmlStream, Class<T> objectType) throws IOException, XMLException, XMLMarshalingException {
		return unmarshall(xmlStream, objectType, null);
	}
	
	protected <T, P> void fillAttributeFromXML(Object xmlClassObject, XMLClassField<T, P> attributeField, String attributeName, XMLInputStream xmlStream, String valueStr, StackList<Object> objectStack) throws XMLMarshalingException {

		objectStack.push(xmlClassObject);
		T value = null;
		if (attributeField.adapter() != null) {
			@SuppressWarnings("unchecked")
			P parentObject = attributeField.type().getEnclosingClass() == null ? null : 
				(P) objectStack.findTopMost(attributeField.type().getEnclosingClass()::isInstance);
			try {
				value = attributeField.adapter().adaptType(valueStr, parentObject);
			} catch (ClassCastException e) {
				// if this happens, it indicates that the adapters parent object-type type-argument was probably set to some arbitrary value because the parent argument is not required.
				value = attributeField.adapter().adaptType(valueStr, null);
			}
		} else if (attributeField.isPrimitive()) {
			value = XMLClassField.adaptPrimitive(attributeField.type(), valueStr);
		} else {
			throw new XMLMarshalingException(xmlStream, "attribute is not XML primitive and has no adapter: " + attributeField.field());
		}
		objectStack.pop();
		
		attributeField.assign(xmlClassObject, value, attributeName);
		
	}
	
	protected <T, P> void fillElementFromXML(Object xmlClassObject, XMLClassField<T, P> elementField, String elementName, XMLInputStream xmlStream, ElementDescriptor openingElement, StackList<Object> objectStack) throws IOException, XMLException, XMLMarshalingException {
		
		objectStack.push(xmlClassObject);
		T value = makeObjectFromXML(xmlStream, openingElement, elementField.type(), objectStack);
		objectStack.pop();
		
		elementField.assign(xmlClassObject, value, elementName);
		
	}
	
	protected <T, P> T makeObjectFromXML(XMLInputStream xmlStream, ElementDescriptor openingElement, Class<T> objectType, StackList<Object> objectStack) throws IOException, XMLException, XMLMarshalingException {
		assert openingElement.type() != DescType.CLOSE : "element descriptor can not be a closing element";
	
		@SuppressWarnings("unchecked")
		XMLClassType<T, P> xmlClassType = (XMLClassType<T, P>) this.types.get(objectType);
		if (xmlClassType == null)
			 throw new XMLMarshalingException("the supplied type is not recognized by this marshaler: " + objectType.getName());
		
		@SuppressWarnings("unchecked")
		P parentObject = xmlClassType.isStatic() ? null : (P) objectStack.findTopMost(xmlClassType.parentType()::isInstance);
		if (!xmlClassType.isStatic() && parentObject == null)
			throw new XMLMarshalingException(xmlStream, "non-static class hierarchical error, unable to identify closest parent class to construct from: " + xmlClassType.parentType());
		T xmlClassObject = xmlClassType.factory().makeType(parentObject);
		
		for (String attributeName : openingElement.attributes().keySet()) {
			XMLClassField<?, ?> attributeField = xmlClassType.attributes().get(attributeName);
			if (attributeField == null) {
				attributeField = xmlClassType.attributes().get(XMLClassType.REMAINING_MAP_FIELD);
				if (attributeField == null) {
					Log.defaultLogger().warnt("XML: " + xmlStream.xmlStackPath() + " ", "warning: attribute unknown in java: " + openingElement.namespace() + " > " + openingElement.name() + " > " + attributeName);
					continue;
				}
			}
			fillAttributeFromXML(xmlClassObject, attributeField, openingElement.name(), xmlStream, openingElement.attributes().get(attributeName), objectStack);
		}
		
		if (openingElement.type() != DescType.SELF_CLOSING) {
			StringBuffer elementText = new StringBuffer();
			readelements: while (true) {
				
				ElementDescriptor element;
				while ((element = xmlStream.readNext()) != null) {
					
					if (element.type() == DescType.CLOSE) {
						if (!element.isSameField(openingElement))
							throw new XMLMarshalingException(xmlStream, "improper element close order, element not closed: " + openingElement.namespace() + " > " + openingElement.name()); // this would indicate a problem with the stream
						break readelements;
					} else {
						XMLClassField<?, ?> xmlElementField = xmlClassType.elements().get(element.namespace(), element.name());
						if (xmlElementField == null) {
							xmlElementField = xmlClassType.elements().get(element.namespace(), XMLClassType.REMAINING_MAP_FIELD);
							if (xmlElementField == null) {
								Log.defaultLogger().warnt("XML: " + xmlStream.xmlStackPath() + " ", "warning: unknown field in XML: " + element.namespace() + " > " + element.name());
								
								if (element.type() == DescType.OPEN) {
									// skip the element, read until close reached
									skipelement: while (true) {
										ElementDescriptor e;
										while ((e = xmlStream.readNext()) != null)
											if (e.isSameField(element)) break skipelement;
										if (xmlStream.readAllText() == null)
											throw new XMLMarshalingException(xmlStream, "unexpected EOF while skipping element: " + element.namespace() + " > " + element.name());
									}
								}
								continue;
							}
						}
						
						if (xmlElementField.isPrimitive() || xmlElementField.adapter() != null) {
							// read only text data of the element
							StringBuffer text = new StringBuffer();
							if (element.type() != DescType.SELF_CLOSING) {
								readtext: while (true) {
									ElementDescriptor e;
									while ((e = xmlStream.readNext()) != null)
										if (e.isSameField(element)) break readtext;
									String s = xmlStream.readAllText();
									if (s == null)
										throw new XMLMarshalingException(xmlStream, "unexpected EOF while reading element text: " + element.namespace() + " > " + element.name());
									text.append(s);
								}
							}
							// write variable as if it was an attribute
							fillAttributeFromXML(xmlClassObject, xmlElementField, element.name(), xmlStream, text.toString(), objectStack);
						} else {
							fillElementFromXML(xmlClassObject, xmlElementField, element.name(), xmlStream, element, objectStack);
						}
					}
					
				}
				
				String s = xmlStream.readAllText();
				if (s == null)
					throw new XMLMarshalingException(xmlStream, "unexpected end of XML stream"); // this would indicate a problem with the stream
				elementText.append(s);
				
			}
			
			XMLClassField<?, ?> xmlTextField = xmlClassType.attributes().get(XMLClassType.TEXT_VALUE_FIELD);
			if (xmlTextField != null) {
				fillAttributeFromXML(xmlClassObject, xmlTextField, null, xmlStream, elementText.toString(), objectStack);
			} else if (!elementText.toString().isBlank()) {
				Log.defaultLogger().warnt("XML: " + xmlStream.xmlStackPath() + " ", "warning: unknown element text data: " + openingElement.namespace() + " > " + openingElement.name());
			}
		}
		
		return xmlClassObject;
		
	}
	
}
