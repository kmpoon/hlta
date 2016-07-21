package hk.ust.cse.lantern.data;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an instance in a data set.
 * 
 * @author leonard
 * 
 */
public class Instance extends AbstractList<Integer> implements
		Comparable<Instance> {
	private List<Integer> values;
	private final VariableCollection variables;

	public Instance(final VariableCollection variables) {
		this.variables = variables;
		values = new ArrayList<Integer>(variables.size());
	}

	public Instance(final VariableCollection variables, Iterable<String> strings) {
		this(variables);
		setTextualValues(strings);
	}

	public int compareTo(Instance o) {
		assert values.size() == o.values.size();
		assert variables == o.variables;

		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) != o.values.get(i))
				return values.get(i) - o.values.get(i);
		}

		return 0;
	}

	public final List<Integer> getNumericValues() {
		return values;
	}

	public final List<String> getTextualValues() {
		List<String> result = new ArrayList<String>(variables.size());

		for (int i = 0; i < values.size(); i++) {
			result.add(variables.get(i).getText(values.get(i)));
		}

		return result;
	}

	public void setNumericValues(Iterable<Integer> numbers) {
		values.clear();

		for (Integer i : numbers) {
			values.add(i);
		}
	}

	public void setTextualValues(Iterable<String> strings) {
		values.clear();

		int i = 0;
		for (String s : strings) {
			if (s == null) {
				values.add(NominalVariable.missingNumber);
			} else {
				values.add(variables.get(i).getNumber(s));
			}
			i++;
		}
	}

	@Override
	public Integer get(int index) {
		return values.get(index);
	}

	@Override
	public int size() {
		return values.size();
	}
}
