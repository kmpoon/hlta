package org.latlab.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Some static methods to use with collection classes.
 * 
 * @author leonard
 * 
 */
public class Algorithm {
	/**
	 * Performs a linear search on the specified collection with the specified
	 * predicate to check whether the current item on search is the one looking
	 * for.
	 * 
	 * @param <T>
	 *            type of items in the collection
	 * @param collection
	 *            holds the items for search
	 * @param pred
	 *            predicate to check one item is the one looking for
	 * @return the desirable item or null if no item is found
	 */
	public static <T> T linearSearch(Collection<T> collection,
			Predicate<? super T> pred) {
		for (T item : collection) {
			if (pred.evaluate(item))
				return item;
		}

		return null;
	}

	/**
	 * Gets the index of the target item.
	 * 
	 * @param <T>
	 * @param collection
	 * @param pred
	 * @return
	 */
	public static <T> int indexOf(Collection<T> collection,
			Predicate<? super T> pred) {
		int i = 0;
		for (T item : collection) {
			if (pred.evaluate(item))
				return i;

			i++;
		}

		return -1;
	}

	public static <T> int indexOf(Collection<T> collection, T key) {
		return indexOf(collection, new ReferencePredicate(key));
	}

	public static <T> int indexOf(T[] array, T key) {
		for (int i = 0; i < array.length; i++)
			if (key == array[i])
				return i;

		return -1;
	}

	/**
	 * Combines the specified collection into one large list.
	 * 
	 * @param <T>
	 * @param collections
	 * @return
	 */
//	public static <T> ArrayList<T> combine(
//			Collection<? extends Collection<T>> collections) {
//
//		int count = 0;
//		for (Collection<T> c : collections) {
//			count += c.size();
//		}
//
//		ArrayList<T> result = new ArrayList<T>(count);
//		for (Collection<T> c : collections) {
//			result.addAll(c);
//		}
//
//		return result;
//	}

	/**
	 * Combines the two collections into one array list, so that it holds all
	 * the items from the original two collections.
	 * 
	 * @param <T>
	 *            type of items
	 * @param collection1
	 *            first collection
	 * @param collection2
	 *            second collection
	 * @return combined collection
	 */
//	public static <T> ArrayList<T> combine(Collection<T> collection1,
//			Collection<T> collection2) {
//		ArrayList<Collection<T>> collections = new ArrayList<Collection<T>>(2);
//		collections.add(collection1);
//		collections.add(collection2);
//		return combine(collections);
//	}

//	public static <K, V> HashMap<K, V> combine(
//			Collection<? extends Map<K, V>> collections) {
//		int count = 0;
//		for (Map<K, V> m : collections) {
//			count += m.size();
//		}
//
//		HashMap<K, V> result = new HashMap<K, V>(count);
//		for (Map<K, V> m : collections) {
//			result.putAll(m);
//		}
//
//		return result;
//	}

//	public static <K, V> HashMap<K, V> combine(Map<K, V> c1, Map<K, V> c2) {
//		ArrayList<Map<K, V>> list = new ArrayList<Map<K, V>>(2);
//		list.add(c1);
//		list.add(c2);
//		return combine(list);
//	}

	/**
	 * Filters the elements in the specified collection and put those passing
	 * the specified predicate into the returned list.
	 * 
	 * @param <T>
	 *            type of element contained
	 * @param collection
	 *            holding the elements
	 * @param pred
	 *            used to filter the elements
	 * @return a list containing the filtered elements
	 */
	public static <T> ArrayList<T> filter(Collection<T> collection,
			Predicate<? super T> pred) {
		ArrayList<T> result = new ArrayList<T>();
		for (T item : collection) {
			if (pred.evaluate(item))
				result.add(item);
		}

		return result;
	}

	/**
	 * Filters the elements in the specified collection and put those passing
	 * the specified predicate into the returned list. A converter is applied to
	 * the element before testing it with the predicate.
	 * 
	 * @param <T>
	 *            type of elements in the collection
	 * @param <Converted>
	 *            type of the converted elements in the resulting collection
	 * @param collection
	 *            containing the elements
	 * @param converter
	 *            used to convert the element
	 * @param pred
	 *            for testing the converted element
	 * @return a list containing the converted elements which pass the predicate
	 */
	public static <T, Converted> ArrayList<Converted> filter(
			Collection<T> collection,
			Converter<? super T, Converted> converter,
			Predicate<? super Converted> pred) {
		ArrayList<Converted> result = new ArrayList<Converted>();
		for (T item : collection) {
			Converted c = converter.convert(item);
			if (pred.evaluate(c))
				result.add(c);
		}

		return result;
	}

	/**
	 * Filters a map for those keys having values satisfying the specified
	 * predicate.
	 * 
	 * @param <K>
	 *            key type
	 * @param <V>
	 *            value type
	 * @param map
	 *            map to filter
	 * @param pred
	 *            predicate for filtering
	 * @return filtered keys
	 */
	public static <K, V> ArrayList<K> filter(Map<K, V> map,
			Predicate<? super V> pred) {
		ArrayList<K> result = new ArrayList<K>();
		for (Map.Entry<K, V> entry : map.entrySet()) {
			if (pred.evaluate(entry.getValue())) {
				result.add(entry.getKey());
			}
		}

		return result;
	}

	/**
	 * Converts a collection of items of {@code From} type to {@code To} type
	 * using the specified converter.
	 * 
	 * @param <From>
	 *            original type of item
	 * @param <To>
	 *            target type of item
	 * @param collection
	 *            holding items of original type
	 * @param converter
	 *            used to convert the items
	 * @return array list holding converted types
	 */
	public static <From, To> ArrayList<To> convert(Collection<From> collection,
			Converter<? super From, To> converter) {
		return convertAdd(collection, new ArrayList<To>(collection.size()),
				converter);
	}

	/**
	 * Converts the items in the collection to another type and adds the
	 * converted items in the result collection.
	 * 
	 * @param <From>
	 *            original type of items
	 * @param <To>
	 *            target type of items
	 * @param <Result>
	 *            type of result collection
	 * @param collection
	 *            holds original items
	 * @param result
	 *            holds converted items
	 * @param converter
	 *            used to convert items
	 * @return a collection holding the converted items
	 */
	public static <From, To, Result extends Collection<To>> Result convertAdd(
			Collection<From> collection, Result result,
			Converter<? super From, To> converter) {
		for (From item : collection) {
			result.add(converter.convert(item));
		}

		return result;
	}

	public static <From, To> ArrayList<To> castTo(Collection<From> collection,
			Class<To> c) {
		return convert(collection, Caster.create(c));
	}

	/**
	 * Creates a map from an item to its index in the map.
	 * 
	 * @param <T>
	 *            type of item
	 * @param list
	 *            list holding the items, of which the index is used
	 * @return a map from an item to its index
	 */
	public static <T> Map<T, Integer> createIndexMap(List<T> list) {
		HashMap<T, Integer> map = new HashMap<T, Integer>(list.size());
		for (int i = 0; i < list.size(); i++) {
			map.put(list.get(i), i);
		}

		return map;
	}

	/**
	 * Creates a map from an item to its index in the map.
	 * 
	 * @param <T>
	 *            type of item
	 * @param array
	 *            array holding the items, of which the index is used
	 * @return a map from an item to its index
	 */
	public static <T> Map<T, Integer> createIndexMap(T[] array) {
		HashMap<T, Integer> map = new HashMap<T, Integer>(array.length);
		for (int i = 0; i < array.length; i++) {
			map.put(array[i], i);
		}

		return map;
	}

	/**
	 * Selects the second part of the pairs and returns them as a collection.
	 * 
	 * @param <T1>
	 * @param <T2>
	 * @param pairs
	 * @return
	 */
	public static <T1, T2> Collection<T2> select2nd(
			Collection<? extends Pair<T1, T2>> pairs) {
		ArrayList<T2> result = new ArrayList<T2>(pairs.size());

		for (Pair<T1, T2> pair : pairs) {
			result.add(pair.second);
		}

		return result;
	}
}
