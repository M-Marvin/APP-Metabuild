package de.m_marvin.basicxml.marshalling.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import de.m_marvin.basicxml.marshalling.XMLClassFieldAdapter;

@Retention(RetentionPolicy.RUNTIME)
public @interface XMLTypeAdapter {
	
	public Class<? extends XMLClassFieldAdapter<?, ?>> value();
	
}
