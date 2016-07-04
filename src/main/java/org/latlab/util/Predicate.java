package org.latlab.util;

/**
 * A predicate checks whether one item is evaluated to satisfy a particular
 * condition specified by it.
 * 
 * @author leonard
 * 
 * @param <T>
 *            Type of the element to be checked.
 */
public interface Predicate<T> {
	/**
	 * Returns whether this predicate is evaluated to be true for the specified
	 * item.
	 * 
	 * @param t
	 *            item to be checked.
	 * @return Whether this predicate is evaluated to be true.
	 */
	boolean evaluate(T t);
}
