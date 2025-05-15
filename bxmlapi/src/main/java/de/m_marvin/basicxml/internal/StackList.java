package de.m_marvin.basicxml.internal;

import java.util.ArrayList;
import java.util.function.Predicate;

public class StackList<E> extends ArrayList<E> {
	
	private static final long serialVersionUID = -3710112911757367048L;
	
	public void push(E value) {
		add(value);
	}
	
	public E pop() {
		if (isEmpty()) return null;
		return remove(size() - 1);
	}
	
	public E peek() {
		if (isEmpty()) return null;
		return get(size() - 1);
	}

	public E findTopMost(Predicate<E> predicate) {
		for (int i = size(); i > 0; i--) {
			E obj = get(i - 1);
			if (predicate.test(obj)) return obj;
		}
		return null;
	}
	
}
