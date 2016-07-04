package org.latlab.util;

public class ReferencePredicate implements Predicate<Object> {
	public ReferencePredicate(Object key) {
		this.key = key;
	}
	
	public boolean evaluate(Object t) {
		return t == key;
	}

	private final Object key;
}
