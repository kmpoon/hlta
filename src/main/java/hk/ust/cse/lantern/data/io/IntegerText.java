package hk.ust.cse.lantern.data.io;

import java.util.*;

/**
 * Provides support functions for those texts that represent some integer
 * values.
 * 
 * @author leonard
 * 
 */
public class IntegerText {
	/**
	 * Compares the integer values represented by two texts, so that {@code 2}
	 * goes before {@code 10}.
	 * 
	 * @author leonard
	 * 
	 */
	public static class TextComparator implements Comparator<String> {

		public int compare(String arg0, String arg1) {
			int value0 = Integer.parseInt(arg0);
			int value1 = Integer.parseInt(arg1);

			return new Integer(value0).compareTo(value1);
		}

	}

	public static boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean isIntegerList(List<String> list) {
		for (String s : list) {
			if (!isInteger(s))
				return false;
		}

		return true;
	}

	public static void sort(List<String> list) {
		Collections.sort(list, new TextComparator());
	}
}