package de.m_marvin.metabuild.util;

import java.util.NoSuchElementException;
import java.util.Optional;

public class SkipOptional<T> {

	private final T value;
	private final boolean skipped;
	
	protected SkipOptional(T value, boolean skipped) {
		this.value = value;
		this.skipped = skipped;
	}
	
	public static <T> SkipOptional<T> of(Optional<T> optional) {
		return optional.isPresent() ? of(optional.get()) : empty();
	}
	
	public static <T> SkipOptional<T> of(T value) {
		if (value == null) throw new NullPointerException("optional value cant be null!");
		return new SkipOptional<T>(value, false);
	}
	
	public static <T> SkipOptional<T> ofNullable(T value) {
		return new SkipOptional<T>(value, false);
	}
	
	public static <T> SkipOptional<T> empty() {
		return new SkipOptional<T>(null, false);
	}
	
	public static <T> SkipOptional<T> skipped() {
		return new SkipOptional<T>(null, true);
	}
	
	public boolean isPresent() {
		return !this.skipped && this.value != null;
	}
	
	public boolean isEmpty() {
		return !this.skipped && this.value == null;
	}

	public boolean isSkipped() {
		return this.skipped;
	}
	
	public T get() {
		if (isEmpty()) throw new NoSuchElementException("cant get value from empty optional!");
		return this.value;
	}
	
}
