package de.m_marvin.basicxml.marshalling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * Marks an XMLField of type {@link FieldType#TEXT}, {@link FieldType#ELEMENT}, {@link FieldType#ELEMENT_COLLECTION} or {@link FieldType#REMAINING_ELEMENT_MAP} to use an CDATA block when writing its text data.
 */
public @interface XMLCDATA {}
