package de.m_marvin.basicxml.marshalling;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import de.m_marvin.archiveutility.ArchiveAccess;
import de.m_marvin.archiveutility.classes.ArchiveClasses;
import de.m_marvin.basicxml.XMLException;
import de.m_marvin.basicxml.XMLInputStream;
import de.m_marvin.basicxml.XMLInputStream.DescType;
import de.m_marvin.basicxml.XMLInputStream.ElementDescriptor;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;

public class XMLMarshaler {
	
	private static class TypeObject<T, P> {
		
		@FunctionalInterface
		public static interface TypeFactory<T, P> {
			public T makeType(P parentObject) throws LayerInstantiationException;
		}
		
		public final boolean isStatic;
		public final TypeFactory<T, P> factory;
		public final Set<Class<?>> subTypes;
		public final Map<Integer, Field> attributes;
		public final Map<Integer, Field> elements;
		
		public TypeObject(Class<T> type, Class<P> parentType) {
			Objects.requireNonNull(type, "type can not be null");
			
			if (!type.isAnnotationPresent(XMLType.class))
				throw new IllegalArgumentException("the supplied class is not annotated as an XML type object");
			
			this.isStatic = Modifier.isStatic(type.getModifiers());
			if (!this.isStatic && parentType == null)
				throw new IllegalArgumentException("type is not a static class but parent type is null");
			try {
				Constructor<T> constructor = this.isStatic ? type.getDeclaredConstructor() : type.getDeclaredConstructor(parentType);
				this.factory = this.isStatic ? parentObject -> {
					try {
						return constructor.newInstance();
					} catch (ExceptionInInitializerError | InvocationTargetException e) {
						throw new LayerInstantiationException("unable to construct type object", e);
					} catch (IllegalArgumentException | InstantiationException | IllegalAccessException e) {
						throw new LayerInstantiationException("construction of object threw an error", e);
					}
				} : parentObject -> {
					try {
						return constructor.newInstance(parentObject);
					} catch (ExceptionInInitializerError | InvocationTargetException e) {
						throw new LayerInstantiationException("unable to construct type object", e);
					} catch (IllegalArgumentException | InstantiationException | IllegalAccessException e) {
						throw new LayerInstantiationException("construction of object threw an error", e);
					}
				};
				
				this.subTypes = new HashSet<Class<?>>();
				this.attributes = new HashMap<Integer, Field>();
				this.elements = new HashMap<Integer, Field>();
				findFieldsAndTypes(type);
				
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("the supplied type class has no default constructor");
			}
		}
		
		private void findFieldsAndTypes(Class<?> clazz) {
			Class<?> superclass = clazz.getSuperclass();
			if (superclass.isAnnotationPresent(XMLType.class))
				findFieldsAndTypes(superclass);
			
			for (Field field : clazz.getDeclaredFields()) {
				XMLField xmlField = field.getAnnotation(XMLField.class);
				if (xmlField == null) continue;
				String name = xmlField.name().equals(XMLField.NULL_STR) ? field.getName() : xmlField.name();
				String namespace = xmlField.namespace().equals(XMLField.NULL_STR) ? null : xmlField.namespace();
				
				switch (xmlField.value()) {
				case ATTRIBUTE: attributes.put(null, field);
				case ELEMENT: elements.put(null, field);
				}
			}
			
			for (Class<?> type : clazz.getDeclaredClasses()) {
				if (type.isAnnotationPresent(XMLType.class))
					this.subTypes.add(type);
			}
		}
		
	}
	
	private final Map<Class<?>, TypeObject<?, ?>> types = new HashMap<>();
	
	public XMLMarshaler(Class<?>... types) {
		for (Class<?> type : types) {
			var typeObj = new TypeObject<>(type, null);
			
		}
	}
	
	public <T> T unmarshall(XMLInputStream xmlStream, Class<T> objectType) throws IOException, XMLException {
		
		ElementDescriptor element = xmlStream.readNext();
		if (element == null) return null;
		
		makeObjectFromXML(xmlStream, element, objectType);
		
		return null;
		
	}
	
	protected <T> T makeObjectFromXML(XMLInputStream xmlStream, ElementDescriptor openingElement, Class<T> objectType) {
		assert openingElement.type() != DescType.CLOSE : "element descriptor can not be a closing element";
		
		try {
			
			Constructor<T> objectConstructor = objectType.getDeclaredConstructor();
			T object = objectConstructor.newInstance();
			
			for (String attributeName : openingElement.attributes().keySet()) {
				
				//objectType.getDeclaredFields()[0].an
				
			}
			
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
	
}
