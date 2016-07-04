package org.latlab.util;

/**
 * Casts an object from one type 
 * @author leonard
 *
 * @param <From>
 * @param <To>
 */
public class Caster<To> implements Converter<Object, To> {

	@SuppressWarnings("unchecked")
	public To convert(Object o) {
		return (To) o;
	}
	
	public static <T> Caster<T> create(Class<T> c) {
		return new Caster<T>();
	}
}
