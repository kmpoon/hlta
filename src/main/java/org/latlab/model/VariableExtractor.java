package org.latlab.model;

import org.latlab.util.Converter;
import org.latlab.util.Variable;

/**
 * Extracts the variable from a belief node.
 * 
 * @author leonard
 * 
 */
public class VariableExtractor implements Converter<BeliefNode, Variable> {

	public Variable convert(BeliefNode node) {
		return node.getVariable();
	}

}
