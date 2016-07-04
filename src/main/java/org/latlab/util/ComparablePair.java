package org.latlab.util;

/**
 * A pair that is comparable.
 * 
 * @author leonard
 * 
 * @param <T1>
 *            type of first item
 * @param <T2>
 *            type of second item
 */
public class ComparablePair<T1 extends Comparable<T1>, T2 extends Comparable<T2>>
		extends Pair<T1, T2> implements Comparable<Pair<T1, T2>> {

	public ComparablePair() {
		this(null, null);
	}

	public ComparablePair(T1 o1, T2 o2) {
		super(o1, o2);
	}

	public int compareTo(Pair<T1, T2> o) {
		int result = first.compareTo(o.first);
		return result != 0 ? result : second.compareTo(o.second);
	}

}
