package de.m_marvin.basicxml.marshaling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/**
 * Determines the order in which elements and attributes of this XML type class are written to an XML file.
 */
public @interface XMLOrder {
	
	public String[] attributes() default {};
	public String[] elements() default {};
	
}
