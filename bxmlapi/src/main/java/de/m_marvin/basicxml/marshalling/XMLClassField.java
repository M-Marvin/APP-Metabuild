package de.m_marvin.basicxml.marshalling;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.basicxml.marshalling.annotations.XMLTypeAdapter;

public record XMLClassField<V, P>(
		boolean isPrimitive,
		FieldType fieldType,
		Field field,
		Class<V> type,
		XMLClassFieldAdapter<V, P> adapter
		) {
	
	public static enum FieldType {
		SINGLE_VALUE,
		VALUE_COLLECTION,
		REMAINING_MAP;
	}
	
	@SuppressWarnings("unchecked")
	public static <V, P> XMLClassField<V, P> makeFromField(Class<V> type, Field field) {
		Objects.requireNonNull(field, "field can not be null");
		
		if (!field.isAnnotationPresent(XMLField.class))
			throw new IllegalArgumentException("the supplied field is not annotated as an XML type object");
		
		XMLField xmlFieldAnnotation = field.getAnnotation(XMLField.class);
		XMLTypeAdapter xmlTypeAdapterAnnotation = field.getAnnotation(XMLTypeAdapter.class);
		
		FieldType fieldType = null;
		Class<V> dataType = null;
		switch (xmlFieldAnnotation.value()) {
		case ATTRIBUTE:
		case ELEMENT:
		case TEXT:
			fieldType = FieldType.SINGLE_VALUE;
			dataType = (Class<V>) field.getType();
			break;
		case ATTRIBUTE_COLLECTION:
		case ELEMENT_COLLECTION:
			fieldType = FieldType.VALUE_COLLECTION;
			dataType = (Class<V>) xmlFieldAnnotation.type();
			if (dataType == Void.class)
				throw new IllegalArgumentException("element collection field requires type parameter in annotation: " + field);
			if (!Collection.class.isAssignableFrom(field.getType()))
				throw new IllegalArgumentException("element collection field must be a subclass of collection: " + field);
			break;
		case REMAINING_ATTRIBUTE_MAP:
		case REMAINING_ELEMENT_MAP:
			fieldType = FieldType.REMAINING_MAP;
			dataType = (Class<V>) xmlFieldAnnotation.type();
			if (dataType == Void.class)
				throw new IllegalArgumentException("remaining element map field requires type parameter in annotation: " + field);
			if (!Map.class.isAssignableFrom(field.getType()))
				throw new IllegalArgumentException("remaining element map field must be a subclass of map: " + field);
			break;
		}
		
		boolean isPrimitive = dataType.isPrimitive() || dataType == String.class;
		
		XMLClassFieldAdapter<V, P> adapter = null;
		if (xmlTypeAdapterAnnotation != null) {
			Class<? extends XMLClassFieldAdapter<?, ?>> adapterClass = xmlTypeAdapterAnnotation.value();
			try {
				if (adapterClass.getEnclosingClass() != null && Modifier.isStatic(adapterClass.getModifiers()))
					throw new IllegalArgumentException("the supplied type adapter class must not be non-static");
				Constructor<? extends XMLClassFieldAdapter<?, ?>> constructor = adapterClass.getConstructor();
				adapter = (XMLClassFieldAdapter<V, P>) constructor.newInstance();
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("the supplied field's type adapter has no default constructor");
			} catch (InstantiationException | InvocationTargetException | IllegalArgumentException | IllegalAccessException e) {
				throw new LayerInstantiationException("failed to construct the type adapter instance for the supplied field", e);
			}
		}
		
		if (adapter == null) {
			XMLTypeAdapter fallbackAdapterAnnotation = dataType.getAnnotation(XMLTypeAdapter.class);
			if (fallbackAdapterAnnotation != null) {
				try {
					adapter = (XMLClassFieldAdapter<V, P>) fallbackAdapterAnnotation.value().getConstructor().newInstance();
				} catch (NoSuchMethodException e) {
					throw new IllegalArgumentException("the supplied field's fallback type adapter has no default constructor");
				}  catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
					throw new LayerInstantiationException("failed to construct the type fallback adapter instance for the supplied field type", e);
				}
			}
		}
		
		if (!dataType.isAnnotationPresent(XMLType.class) && adapter == null && !isPrimitive)
			throw new IllegalArgumentException("field type requires type adapter: " + field);
		
		return new XMLClassField<V, P>(isPrimitive, fieldType, field, dataType, adapter);
		
	}
	
	public void assign(Object xmlClassObject, V value, String key) {
		try {
			switch (this.fieldType) {
			case SINGLE_VALUE:
				this.field.set(xmlClassObject, value);
				break;
			case VALUE_COLLECTION:
				@SuppressWarnings("unchecked")
				Collection<V> collection = (Collection<V>) this.field.get(xmlClassObject);
				if (collection == null) {
					boolean isSet = Set.class.isAssignableFrom(this.field.getType());
					collection = isSet ? new HashSet<V>() : new ArrayList<V>();
					this.field.set(xmlClassObject, collection);
				}
				collection.add(value);
				break;
			case REMAINING_MAP:
				@SuppressWarnings("unchecked")
				Map<String, V> map = (Map<String, V>) this.field.get(xmlClassObject);
				if (map == null) {
					map = new HashMap<String, V>();
					this.field.set(xmlClassObject, map);
				}
				map.put(key, value);
				break;
			}
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("the supplied type does not match the fields type", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("the field is not accessible", e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T adaptPrimitive(Class<T> primitive, String valueStr) {
		if (primitive == String.class) {
			return (T) valueStr;
		} else if (primitive == Boolean.class || primitive == boolean.class) {
			return (T) Boolean.valueOf(valueStr);
		} else if (primitive == Integer.class || primitive == int.class) {
			return (T) Integer.valueOf(valueStr);
		} else if (primitive == Short.class || primitive == short.class) {
			return (T) Short.valueOf(valueStr);
		} else if (primitive == Long.class || primitive == long.class) {
			return (T) Long.valueOf(valueStr);
		} else if (primitive == Double.class || primitive == double.class) {
			return (T) Double.valueOf(valueStr);
		} else if (primitive == Float.class || primitive == float.class) {
			return (T) Float.valueOf(valueStr);
		}
		throw new IllegalArgumentException("supplied class is not an XML primitive");
	}

	public static int getFieldHash(URI namespace, String name) {
		return getFieldHash(namespace == null ? null : namespace.toString(), name);
	}

	public static int getFieldHash(String namespace, String name) {
		return Objects.hash(namespace, name);
	}
	
}
