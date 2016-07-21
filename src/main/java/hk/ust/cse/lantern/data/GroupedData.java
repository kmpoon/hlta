package hk.ust.cse.lantern.data;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * It groups the instances with the same values together and associate a count
 * with each possible instance.
 * 
 * @author leonard
 * 
 */
public class GroupedData {
	/**
	 * Holds the counts for each of the configurations in data
	 */
	private TreeMap<Instance, Integer> configurations = new TreeMap<Instance, Integer>();

	private VariableCollection variables;

	public GroupedData(Data data) {
		variables = data.variables();

		for (Instance instance : data.instances())
			addInstance(instance);
	}

	private void addInstance(Instance instance) {
		Integer previousCount = configurations.get(instance);
		if (previousCount == null)
			configurations.put(instance, 1);
		else
			configurations.put(instance, previousCount + 1);
	}

	public SortedMap<Instance, Integer> getInstances() {
		return configurations;
	}

	public VariableCollection getVariables() {
		return variables;
	}

}
