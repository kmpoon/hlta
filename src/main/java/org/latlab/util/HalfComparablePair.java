package org.latlab.util;

/**
 * A pair that is half comparable, i.e., only the first item is comparable.
 * 
 * @author leonard
 * 
 * @param <T1>
 *            type of first item
 * @param <T2>
 *            type of second item
 */
public class HalfComparablePair<T1 extends Comparable<T1>, T2> extends
		Pair<T1, T2> implements Comparable<Pair<T1, T2>> {

	public HalfComparablePair() {
		this(null, null);
	}

	public HalfComparablePair(T1 o1, T2 o2) {
		super(o1, o2);
	}

	public int compareTo(Pair<T1, T2> o) {
		return first.compareTo(o.first);
	}

}
