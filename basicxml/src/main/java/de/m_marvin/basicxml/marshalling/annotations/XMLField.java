package de.m_marvin.basicxml.marshalling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Marks an field of an class as taking part in the XML (un)marshaling
 */
public @interface XMLField {
	
	/**
	 * Decides how this field is treated by the XML (un)marshaler
	 */
	public static enum FieldType {
		/**
		 * This field will be set from an XML attribute of this class's XML element
		 */
		ATTRIBUTE,
		/**
		 * This field will be set by all XML attributes of this class's XML element which did not have an dedicated field of ATTRIBUTE or ATTRIBUTE_COLLECTION type assigned
		 */
		REMAINING_ATTRIBUTE_MAP,
		/**
		 * This field will be set from an XML element which is contained within this class's element
		 */
		ELEMENT,
		/**
		 * This field will be set from one or more XML elements which is contained within this class's element
		 */
		ELEMENT_COLLECTION,
		/**
		 * This field will be set from all XML elements which are contained within this class's element and did not have an dedicated field of type ELEMENT or ELEMENT_COLLECTION type assigned
		 */
		REMAINING_ELEMENT_MAP,
		/**
		 * This field will be set from the text data contained within this class's XML element
		 */
		TEXT;
	}
	
	/**
	 * Type of this field
	 */
	public FieldType value();
	
	/**
	 * Placeholder string for null value, which is not permitted in annotations
	 */
	public static final String NULL_STR = "<null>";
	
	/**
	 * Override the name of the XML entry, which by default is the name of the class field
	 */
	public String name() default NULL_STR;
	
	/**
	 * Set the namespace of the XML entry, by default no namespace is defined
	 */
	public String namespace() default NULL_STR;
	
	/**
	 * The type of this field, only required for collection or map fields, ignored by normal single value fields
	 */
	public Class<?> type() default Void.class;
	
}
