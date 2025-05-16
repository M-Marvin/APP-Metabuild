package de.m_marvin.basicxml.marshalling.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import de.m_marvin.basicxml.marshalling.XMLMarshalingException;
import de.m_marvin.basicxml.marshalling.adapter.XMLClassFieldAdapter;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.basicxml.marshalling.annotations.XMLTypeAdapter;
import de.m_marvin.simplelogging.Log;

/**
 * Describes information about an class field required for XML marshaling
 * @param <V> The value type of the field
 * @param <P> The type of the parent class in which the fields data type class is defined, used when construction non-static classes
 */
public record XMLClassField<V, P>(
		/** true if this fields data type is an XML primitive (java primitives + string and enums) **/
		boolean isPrimitive,
		/** type of this fields data container, can be a single value, a list or an map **/
		FieldType fieldType,
		/** java field that this class is describing */
		Field field,
		/** data type of the field **/
		Class<V> type,
		/** optional type adapter used for converting to the fields data type **/
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
		
		boolean isPrimitive = dataType.isPrimitive() || dataType == String.class || dataType.isEnum();
		
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
	
	public void assign(Object xmlClassObject, V value, String key) throws XMLMarshalingException {
		try {
			switch (this.fieldType) {
			case SINGLE_VALUE:
				this.field.set(xmlClassObject, value);
				break;
			case VALUE_COLLECTION:
				@SuppressWarnings("unchecked")
				Collection<V> collection = (Collection<V>) this.field.get(xmlClassObject);
				if (collection == null) {
					try {
						@SuppressWarnings("unchecked")
						Constructor<Collection<V>> collectionConstructor = (Constructor<Collection<V>>) this.field.getType().getConstructor();
						collection = collectionConstructor.newInstance();
					} catch (NoSuchMethodException e) {
						throw new XMLMarshalingException("the collection class does not have an default constructor, and no instance if provided: " + this.field, e);
					} catch (InstantiationException | SecurityException | InvocationTargetException e) {
						throw new XMLMarshalingException("the collection class could not be constructed: " + this.field, e);
					}
					this.field.set(xmlClassObject, collection);
				}
				collection.add(value);
				break;
			case REMAINING_MAP:
				@SuppressWarnings("unchecked")
				Map<String, V> map = (Map<String, V>) this.field.get(xmlClassObject);
				if (map == null) {
					try {
						@SuppressWarnings("unchecked")
						Constructor<Map<String, V>> mapConstructor = (Constructor<Map<String, V>>) this.field.getType().getConstructor();
						map = mapConstructor.newInstance();
					} catch (NoSuchMethodException e) {
						throw new XMLMarshalingException("the map class does not have an default constructor, and now instance if provided: " + this.field, e);
					} catch (InstantiationException | SecurityException | InvocationTargetException e) {
						throw new XMLMarshalingException("the map class could not be constructed: " + this.field, e);
					}
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
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
		} else if (primitive.isEnum()) {
			if (valueStr == null) return null;
			for (T e : primitive.getEnumConstants())
				if (((Enum) e).name().equalsIgnoreCase(valueStr)) return e;
			Log.defaultLogger().warn("warning: unknown enum constant in XML: " + primitive + "." + valueStr);
			return null;
		}
		throw new IllegalArgumentException("supplied class is not an XML primitive");
	}
	
}
