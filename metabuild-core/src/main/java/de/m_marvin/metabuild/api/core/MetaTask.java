package de.m_marvin.metabuild.api.core;

import java.util.Optional;

public record MetaTask<T>(T ref, Optional<MetaGroup<T>> group, String name) {}
