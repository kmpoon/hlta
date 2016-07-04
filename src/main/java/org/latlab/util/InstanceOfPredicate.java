package org.latlab.util;

/**
 * Tests whether an object is an instance of a particular type.
 * 
 * @author leonard
 * 
 * @param <T>
 *            type against which an object is tested
 */
public class InstanceOfPredicate<T> implements Predicate<T> {

	/**
	 * Constructor.
	 * 
	 * @param c
	 *            class object of which another object is tested against the
	 *            type
	 */
	public InstanceOfPredicate(Class<? extends T> c) {
		this.c = c;
	}

	/**
	 * Returns whether {@code t} is of the underlying type.
	 */
	public boolean evaluate(Object t) {
		return c.isInstance(t);
	}

	private Class<? extends T> c;
}
