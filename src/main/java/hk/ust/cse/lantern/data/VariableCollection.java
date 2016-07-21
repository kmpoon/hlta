package hk.ust.cse.lantern.data;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds a collection of variables.
 * 
 * @author leonard
 * 
 */
public class VariableCollection extends AbstractList<Variable> {

	private static final long serialVersionUID = -291636629108998675L;
	
	private final List<Variable> variables;

	public VariableCollection() {
		variables = new ArrayList<Variable>();
	}

	public VariableCollection(int initialCapacity) {
		variables = new ArrayList<Variable>(initialCapacity);
	}
	
	public VariableCollection(VariableCollection variables) {
		this.variables = new ArrayList<Variable>(variables);
	}

	/**
	 * Checks whether this collection of variables is of the same kind of
	 * variables.
	 * 
	 * @param <T>
	 *            type of the variable to check against
	 * @param c
	 *            class object of the variable to check against
	 * @return whether this collection of the same type as specified
	 */
	public <T extends Variable> boolean isSameKind(Class<T> c) {
		for (Variable v : this) {
			if (v.getClass() != c) {
				return false;
			}
		}

		return true;
	}

	@Override
	public Variable get(int index) {
		return variables.get(index);
	}

	@Override
	public int size() {
		return variables.size();
	}
	
	@Override
	public Variable set(int index, Variable varaible) {
		return variables.set(index, varaible);
	}
	
	@Override
	public void add(int index, Variable variable) {
		variables.add(index, variable);
	}
	
	@Override
	public Variable remove(int index) {
		return variables.remove(index);
	}
}
