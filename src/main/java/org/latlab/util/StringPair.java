package org.latlab.util;

public class StringPair implements Comparable<StringPair> {

	/**
	 * String A.
	 */
	private String _a;

	/**
	 * String B.
	 */
	private String _b;

	/**
	 * The weight of this string pair.
	 */
	private double _weight;

	/**
	 * Constructor.
	 * 
	 * @param a
	 *            String A.
	 * @param b
	 *            String B.
	 * @param weight
	 *            The weight of this string pair.
	 */
	public StringPair(String a, String b, double weight) {
		_a = a;
		_b = b;
		_weight = weight;
	}

	/**
	 * Return the string _a.
	 * 
	 * @return
	 */
	public String GetStringA()
	{
		return _a;
	}
	
	/**
	 * Return the string _b.
	 * @return
	 */
	public String GetStringB()
	{
		return _b;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(StringPair pair) {
		return Double.compare(_weight,pair._weight);
//		if (_weight > pair._weight) {
//			return 1;
//		} else if (_weight < pair._weight) {
//			return -1;
//		}
//		return 0;
	}

}
