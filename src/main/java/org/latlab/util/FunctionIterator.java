package org.latlab.util;

import java.util.Arrays;
import java.util.List;

/**
 * Used to iterate the values of the states in this function.
 * 
 * @author leonard
 * 
 */
public class FunctionIterator {
	public interface Visitor {
		public void visit(List<Variable> order, int[] states, double value);
	}

	/**
	 * Constructs a iterator for the given function, using the given order. If
	 * the number of variables in the order list is smaller than that in the
	 * function, the missing variables are not iterated, and the state zero is
	 * assumed for these missing variables.
	 * 
	 * @param function
	 *            function to iterate with
	 * @param order
	 *            order of the variables in the iteration
	 */
	public FunctionIterator(Function function, List<Variable> order) {
		this.function = function;
		this.order = order;

		// initialize the index map
		List<Variable> originalOrder = function.getVariables();
		assert order.size() < originalOrder.size();
		indexMap = new int[order.size()];
		for (int i = 0; i < order.size(); i++) {
			indexMap[i] = originalOrder.indexOf(order.get(i));
		}
	}

	public void iterate(Visitor visitor) {
		int[] states = new int[function.getVariables().size()];
		Arrays.fill(states, 0);

		int[] statesInOriginalOrder = new int[function.getVariables().size()];
		Arrays.fill(statesInOriginalOrder, 0);
		iterate(visitor, states, statesInOriginalOrder, 0);
	}

	private void iterate(Visitor visitor, int[] states,
			int[] statesInOriginalOrder, int currentIteratingDimension) {
		if (currentIteratingDimension == order.size()) {
			visit(visitor, states, statesInOriginalOrder);
		} else {
			Variable currentIteratingVariable = order
					.get(currentIteratingDimension);
			for (int state = 0; state < currentIteratingVariable
					.getCardinality(); state++) {
				states[currentIteratingDimension] = state;

				int originalIndex = indexMap[currentIteratingDimension];
				statesInOriginalOrder[originalIndex] = state;

				// iterate in the next dimension
				iterate(visitor, states, statesInOriginalOrder,
						currentIteratingDimension + 1);
			}
		}
	}

	private void visit(Visitor visitor, int states[],
			int[] statesInOriginalOrder) {
		double value = function.getValue(statesInOriginalOrder);
		visitor.visit(order, states, value);
	}

	private final Function function;
	private final List<Variable> order;

	/**
	 * Maps the indices in specified order to the original order
	 */
	private final int[] indexMap;
}
