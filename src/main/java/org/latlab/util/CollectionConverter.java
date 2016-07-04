package org.latlab.util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Converts a collection to another collection of different type
 * 
 * @author leonard
 * 
 */
public class CollectionConverter {
	/**
	 * Converts a collection of {@code From} items to collection of {@code To}
	 * items using the specified converter.
	 * 
	 * @param <From>
	 *            type of items converted from
	 * @param <To>
	 *            type of items converted to
	 * @param collection
	 *            original collection
	 * @param converter
	 *            converts the items from {@code From} to {@code To} type
	 * @return an array list holding the converted type of items
	 */
	public static <From, To> ArrayList<To> toArrayList(
			Collection<From> collection, Converter<? super From, To> converter) {
		ArrayList<To> result = new ArrayList<To>(collection.size());
		for (From f : collection)
			result.add(converter.convert(f));
		return result;
	}
}
