package org.latlab.util;

/**
 * Tests whether two objects are equal in value.
 * 
 * @author leonard
 * 
 * @param <T>
 *            type of element under test
 */
public class EqualPredicate<T> implements Predicate<T> {
	/**
	 * Constructor.
	 * 
	 * @param key
	 *            against which the other objeccts are tested against
	 */
	public EqualPredicate(T key) {
		this.key = key;
	}

	/**
	 * Returns true if {@code T} equals to the {@code key}.
	 */
	public boolean evaluate(T t) {
		return key == null ? key == t : key.equals(t);
	}

	private final T key;
}
