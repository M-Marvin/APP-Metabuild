package de.m_marvin.basicxml.marshalling.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
/**
 * Marks an field of an class as taking part in the XML (un)marshaling
 */
public @interface XMLField {
	
	public static enum FieldType {
		ATTRIBUTE,
		ATTRIBUTE_COLLECTION,
		REMAINING_ATTRIBUTE_MAP,
		ELEMENT,
		ELEMENT_COLLECTION,
		REMAINING_ELEMENT_MAP,
		TEXT;
	}
	
	public FieldType value();
	
	public static final String NULL_STR = "<null>";
	
	public String name() default NULL_STR;
	
	public String namespace() default NULL_STR;
	
	public Class<?> type() default Void.class;
	
}
