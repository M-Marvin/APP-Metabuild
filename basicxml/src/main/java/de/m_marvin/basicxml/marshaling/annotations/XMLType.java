package de.m_marvin.basicxml.marshaling.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
/**
 * Marks an class as being an XML type object which can be used by an (un)marshaler.
 */
public @interface XMLType {}
