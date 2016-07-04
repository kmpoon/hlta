package org.latlab.model;

import org.latlab.util.Predicate;
import org.latlab.util.Variable;

/**
 * Checks whether a variable has the same name as a specified string.
 * 
 * @author leonard
 * 
 */
public class VariableNamePredicate implements Predicate<Variable> {
	public VariableNamePredicate(String name) {
		this.name = name;
	}

	public boolean evaluate(Variable variable) {
		return variable.getName().equals(name);
	}

	private final String name;
}
