package de.m_marvin.metabuild.api.core.tasks;

import java.util.Optional;

public record MetaTask<T>(T ref, Optional<MetaGroup<T>> group, String name) {
	
	@Override
	public final boolean equals(Object arg0) {
		if (arg0 instanceof MetaTask other) {
			return other.name.equals(this.name);
		}
		return false;
	}
	
	@Override
	public final int hashCode() {
		return this.name.hashCode();
	}
	
}
