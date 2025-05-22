package de.m_marvin.basicxml.marshaling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/**
 * Marks an type of being able to be serialized using an marshaler, and sets the root element namespace and name.
 */
public @interface XMLRootType {
	
	public String value();
	public String namespace() default XMLField.NULL_STR;
	
}
