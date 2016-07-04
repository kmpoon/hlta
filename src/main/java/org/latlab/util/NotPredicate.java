package org.latlab.util;

/**
 * Used to negate a provided predicate.
 * 
 * @author leonard
 * 
 * @param <T>
 *            Type on that the predicate evaluates
 */
public class NotPredicate<T> implements Predicate<T> {

	/**
	 * Constructs this predicate with another predicate.
	 * 
	 * @param pred
	 *            the result of which is negated to become the result of this
	 *            predicate
	 */
	public NotPredicate(Predicate<? super T> pred) {
		this.pred = pred;
	}

	/**
	 * Returns the negated result of the underlying predicate.
	 */
	public boolean evaluate(T t) {
		return !pred.evaluate(t);
	}

	private final Predicate<? super T> pred;
}
