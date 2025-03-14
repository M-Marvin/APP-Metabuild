package de.m_marvin.metabuild.api.core.tasks;

public record MetaGroup<T>(T ref, String group) {

	@Override
	public final boolean equals(Object arg0) {
		if (arg0 instanceof MetaGroup other) {
			return other.group.equals(this.group);
		}
		return false;
	}
	
	@Override
	public final int hashCode() {
		return this.group.hashCode();
	}
	
}
