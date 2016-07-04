/**
 * Variable.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.util;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * This class provides an implementation for nominal variables. Although the
 * states of a nominal variable is not ordered, we index them by 0, 1, ...,
 * (cardinality - 1) in this implementation.
 * </p>
 * 
 * <p>
 * Note: This class has a natural ordering that is inconsistent with equals.
 * </p>
 * 
 * @author Yi Wang
 * 
 */
public class Variable implements Comparable {

	/**
	 * the common prefix of default names of variables.
	 */
	private final static String VARIABLE_PREFIX = "variable";

	/**
	 * the common prefix of default names of states.
	 */
	private final static String STATE_PREFIX = "state";

	/**
	 * the number of variable instances that have ever been created.
	 */
	private static int _count = 0;

	/**
	 * Returns the default name for the next variable instance. If you have no
	 * preference on the name of the next variable instance, use this method to
	 * obtain the default name.
	 * 
	 * @return the default name for the next variable instance.
	 */
	private final static String createDefaultName() {
		return (VARIABLE_PREFIX + _count);
	}

	/**
	 * Returns the list of default names of states for the specified
	 * cardinality. If you have no preference on names of states of the next
	 * variable instance, use this method to obtain the list of default names.
	 * 
	 * @param cardinality
	 *            number of states.
	 * @return the list of default names of states for the specified
	 *         cardinality.
	 */
	private final static ArrayList<String> createDefaultStates(int cardinality) {

		assert cardinality > 0;

		ArrayList<String> states = new ArrayList<String>();

		for (int i = 0; i < cardinality; i++) {
			states.add(STATE_PREFIX + i);
		}

		return states;
	}

	/**
	 * the index of this variable that indicates when it was created.
	 */
	private int _index;

	/**
	 * the name of this variable.
	 */
	private String _name;

	/**
	 * the list of states of this variable.
	 */
	private ArrayList<String> _states;

	/**
	 * Constructs a variable with the specified name and the specified list of
	 * states.
	 * 
	 * @param name
	 *            name of the variable to be created.
	 * @param states
	 *            list of states of the variable to be created.
	 */
	public Variable(String name, ArrayList<String> states) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		// states cannot be empty
		assert !states.isEmpty();

		_index = _count++;
		_name = name;
		_states = states;
	}

	/**
	 * Construct a variable with default nama and given number of states.
	 * 
	 * @author csct
	 * @param cardinality
	 *            The cardinality of this Variable.
	 */
	public Variable(int cardinality) {
		this(Variable.createDefaultName(), Variable
				.createDefaultStates(cardinality));
	}

	/**
	 * <p>
	 * Compares this variable with the specified object for order.
	 * </p>
	 * 
	 * <p>
	 * If the specified object is not a variable, this method will throw a
	 * <code>ClassCastException</code> (as variables are comparable only to
	 * other variables). Otherwise, the comparison is carried out based on the
	 * indices of two variables. The result is a negative/positive integer if
	 * this variable was created earlier/later than the specified variable. The
	 * result is zero if they refers to the same variable.
	 * </p>
	 * 
	 * <p>
	 * Note: <code>compareTo(Object)</code> is inconsistent with
	 * <code>equals(Object)</code>.
	 * </p>
	 * 
	 * @param object
	 *            the object to be compared.
	 * @return a negative or a positive integer if this variable was created
	 *         earlier than or later than the specified variable; zero if they
	 *         refers to the same variable.
	 */
	public final int compareTo(Object object) {
		return (_index - ((Variable) object)._index);
	}

	/**
	 * Returns <code>true</code> if the specified object is equals to this
	 * variable. An object is equals to this variable if (1) it is a variable;
	 * and (2) it has the same name and the same list of states as this
	 * variable.
	 * 
	 * @return <code>true</code> if the specified object is equals to this
	 *         variable.
	 */
	@Override
	public final boolean equals(Object object) {
		// tests identity
		if (this == object) {
			return true;
		}

		Variable variable = (Variable) object;
		return (_name.equals(variable._name) && _states
				.equals(variable._states));
	}

	/**
	 * Returns the cardinality of this variable. The cardinality of a nominal
	 * variable equals the number of states that this variable can take.
	 * 
	 * @return the cardinality of this variable.
	 */
	public final int getCardinality() {
		return _states.size();
	}

	/**
	 * Returns the name of this variable.
	 * 
	 * @return the name of this variable.
	 */
	public final String getName() {
		return _name;
	}

	/**
	 * Returns the list of states of this variable.
	 * 
	 * @return the list of states of this variable.
	 */
	public final ArrayList<String> getStates() {
		return _states;
	}

	/**
	 * Returns the index of the specified state in the domain of this variable.
	 * 
	 * @param state
	 *            state whose index is to be returned.
	 * @return the index of the specified state in the domain of this variable.
	 */
	public final int indexOf(String state) {
		return _states.indexOf(state);
	}

	/**
	 * Returns <code>true</code> if the specified integer is a valid state index
	 * in the domain of this variable.
	 * 
	 * @param state
	 *            index of state whose validity is to be tested.
	 * @return <code>true</code> if the specified integer is a valid state index
	 *         in the domain of this variable.
	 */
	public final boolean isValid(int state) {
		return (state >= 0 && state < getCardinality());
	}

	/**
	 * Updates the name of this variable.
	 * 
	 * <p>
	 * Note: Only <code>BeliefNode.setName(String></code> is supposed to call
	 * this method. Abusing this method may cause inconsistency between names of
	 * a belief node and the variable attached to it.
	 * </p>
	 * 
	 * @param name
	 *            new name of this variable.
	 */
	public void setName(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		_name = name;
	}

	/**
	 * Returns a string representation of this Variable. This implementation
	 * returns <code>toString(0)</code>.
	 * 
	 * @return a string representation of this Variable.
	 * @see #toString(int)
	 */
	@Override
	public final String toString() {
		return toString(0);
	}

	/**
	 * Returns a string representation of this Variable. The string
	 * representation will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this Variable.
	 */
	public final String toString(int amount) {
		// amount must be non-negative
		assert amount >= 0;

		// prepares white space for indent
		StringBuffer whiteSpace = new StringBuffer();
		for (int i = 0; i < amount; i++) {
			whiteSpace.append('\t');
		}

		// builds string representation
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(whiteSpace);
		stringBuffer.append("Variable ");
		stringBuffer.append("\"" + _name + "(");
		stringBuffer.append(_states.size() + ")\":");

		for (int i = 0; i < _states.size(); i++) {
			stringBuffer.append(" " + _states.get(i));
		}
		stringBuffer.append(whiteSpace);
		stringBuffer.append("\n");
		return stringBuffer.toString();
	}

	/**
	 * Reorder the states according to the given order.
	 * 
	 * @param order
	 *            shows the desired order, the array index is the position of
	 *            the states, of which the index are held as items.
	 */
	public void reorderStates(int[] order) {
		assert order.length == _states.size();
		List<String> clone = new ArrayList<String>(_states);
		for (int i = 0; i < _states.size(); i++) {
			_states.set(i, clone.get(order[i]));
		}
	}

	/**
	 * Standardizes the state names as s0, s1, ..., etc.
	 */
	public void standardizeStates() {
		for (int i = 0; i < _states.size(); i++) {
			_states.set(i, "s" + i);
		}
	}

}
