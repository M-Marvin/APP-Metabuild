package de.m_marvin.basicxml.marshalling.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import de.m_marvin.basicxml.marshalling.adapter.XMLClassFieldAdapter;

@Retention(RetentionPolicy.RUNTIME)
/**
 * Defines an type adapter to convert the string XML data into the desired class
 */
public @interface XMLTypeAdapter {
	
	public Class<? extends XMLClassFieldAdapter<?, ?>> value();
	
}
