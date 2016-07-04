package org.latlab.util;

/**
 * Converter an object from {@code From} type to {@code To} type
 * 
 * @author leonard
 * 
 * @param <From>
 *            type of object converted from
 * @param <To>
 *            type of object converted to
 */
public interface Converter<From, To> {
	public To convert(From o);
}
