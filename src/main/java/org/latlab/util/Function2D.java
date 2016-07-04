/**
 * Function2D.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.util;

import java.util.ArrayList;

//import hlcm.Function2V;

/**
 * This class provides an implementation for two-dimensional tabular functions,
 * namely, matrices.
 * 
 * @author Yi Wang
 * 
 */
public class Function2D extends Function {

	/**
	 * the shortcut to the only two variables in this function. There is a
	 * requirement that _x>_y.
	 */
	protected Variable _x, _y;

	/**
	 * <p>
	 * Constructs a function of the specified array of variables.
	 * </p>
	 * 
	 * <p>
	 * Note: Only function classes are supposed to call this method.
	 * </p>
	 * 
	 * @param variables
	 *            array of variables to be involved. There are two Variables
	 *            soorted in the Variable array.
	 */
	protected Function2D(Variable[] variables) {
		super(variables);

		_x = _variables[0];
		_y = _variables[1];
	}

	/**
	 * <p>
	 * Constructs a function with all its internal data structures specified.
	 * </p>
	 * 
	 * <p>
	 * Note: Only function classes are supposed to call this method.
	 * </p>
	 * 
	 * @param variables
	 *            array of variables in new function. There are two Variables in
	 *            the Variable array.
	 * @param cells
	 *            array of cells in new function.
	 * @param magnitudes
	 *            array of magnitudes for variables in new function.
	 */
	protected Function2D(Variable[] variables, double[] cells, int[] magnitudes) {
		super(variables, cells, magnitudes);

		_x = _variables[0];
		_y = _variables[1];
	}

	/**
	 * Set cell value.
	 * 
	 * @param var1
	 * @param i
	 * @param var2
	 * @param j
	 * @param value
	 */
	public void setCell(Variable var1, int i, Variable var2, int j, double value) {
		ArrayList<Variable> variables = new ArrayList<Variable>(2);
		ArrayList<Integer> states = new ArrayList<Integer>(2);

		variables.add(var1);
		states.add(i);

		variables.add(var2);
		states.add(j);

		super.setCell(variables, states, value);
	}

	/**
	 * Construct an equal hlcm.Function2V of this 2-d function. Note that the
	 * two hlcm Variables are given not in order.
	 * 
	 * @param var2
	 * @param var2
	 * @return
	 */
//	public Function2V toFunction2V(hlcm.Variable var1, hlcm.Variable var2) {
//		if (_x.getName().equals(var1.getName())
//				&& _y.getName().equals(var2.getName()))
//			return toFunction2VInOrder(var1, var2);
//		else
//			return toFunction2VInOrder(var2, var1);
//
//	}

	/**
	 * Construct an equal hlcm.Function2V of this 2-d function. Note that the
	 * two hlcm Variables are given in order so that _x = var_x and _y = var_y
	 * 
	 * @param var_x
	 * @param var_y
	 * @return
	 */
//	private Function2V toFunction2VInOrder(hlcm.Variable var_x,
//			hlcm.Variable var_y) {
//		Function2V fun_hlcm = new Function2V(var_x, var_y);
//		int index = 0;
//		for (int s_x = 0; s_x < _x.getCardinality(); s_x++)
//			for (int s_y = 0; s_y < _y.getCardinality(); s_y++) {
//				fun_hlcm.setVal(var_x, s_x, var_y, s_y, _cells[index]);
//				index++;
//			}
//		return fun_hlcm;
//	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.latlab.util.Function#normalize(org.latlab.util.Variable)
	 */
	@Override
	public final boolean normalize(Variable variable) {

		// argument variable must be either of the variables in this function
		assert variable == _x || variable == _y;

		boolean hasZero = false;

		int xCard = _x.getCardinality();
		int yCard = _y.getCardinality();

		int index;
		double sum;

		if (variable == _x) {
			// uniform probability that may be used
			double uniform = 1.0 / xCard;

			for (int i = 0; i < yCard; i++) {
				// computes sum
				index = i;
				sum = 0.0;
				for (int j = 0; j < xCard; j++) {
					sum += _cells[index];
					index += yCard;
				}

				// normalizes
				index = i;
				if (sum != 0.0) {
					for (int j = 0; j < xCard; j++) {
						_cells[index] /= sum;
						index += yCard;
					}
				} else {
					for (int j = 0; j < xCard; j++) {
						_cells[index] = uniform;
						index += yCard;
					}

					hasZero = true;
				}
			}
		} else {
			// uniform probability that may be used
			double uniform = 1.0 / yCard;

			index = 0;
			for (int i = 0; i < xCard; i++) {
				// computes sum
				sum = 0.0;
				for (int j = 0; j < yCard; j++) {
					sum += _cells[index++];
				}

				// normalizes
				index -= yCard;
				if (sum != 0.0) {
					for (int j = 0; j < yCard; j++) {
						_cells[index++] /= sum;
					}
				} else {
					for (int j = 0; j < yCard; j++) {
						_cells[index++] = uniform;
					}

					hasZero = true;
				}
			}
		}

		return hasZero;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.latlab.util.Function#project(org.latlab.util.Variable, int)
	 */
	@Override
	public Function project(Variable variable, int state) {

		// For Test
		// System.out.println("Function2D.project(Variable, int) executed");

		// argument variable must be either of the variables in this function
		assert variable == _x || variable == _y;

		// state must be valid
		assert variable.isValid(state);

		// result is an one-dimensional function
		Variable[] variables;
		double[] cells;
		int[] magnitudes = new int[] { 1 };

		if (variable == _x) {
			variables = new Variable[] { _y };

			int yCard = _y.getCardinality();
			cells = new double[yCard];

			System.arraycopy(_cells, state * yCard, cells, 0, yCard);
		} else {
			variables = new Variable[] { _x };

			int xCard = _x.getCardinality();
			int yCard = _y.getCardinality();
			cells = new double[xCard];

			int index = state;
			for (int i = 0; i < xCard; i++) {
				cells[i] = _cells[index];
				index += yCard;
			}
		}

		return (new Function1D(variables, cells, magnitudes));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.latlab.util.Function#sumOut(org.latlab.util.Variable)
	 */
	@Override
	public Function sumOut(Variable variable) {

		// For Test
		// System.out.println("Function2D.sumOut(Variable) executed");

		// argument variable must be either of the variables in this function
		assert variable == _x || variable == _y;

		// result is an one-dimensional function
		Variable[] variables;
		double[] cells;
		int[] magnitudes = new int[] { 1 };

		int xCard = _x.getCardinality();
		int yCard = _y.getCardinality();

		if (variable == _x) {
			variables = new Variable[] { _y };

			cells = new double[yCard];

			int index = 0;
			for (int i = 0; i < xCard; i++) {
				for (int j = 0; j < yCard; j++) {
					cells[j] += _cells[index++];
				}
			}
		} else {
			variables = new Variable[] { _x };

			cells = new double[xCard];

			int index = 0;
			for (int i = 0; i < xCard; i++) {
				for (int j = 0; j < yCard; j++) {
					cells[i] += _cells[index++];
				}
			}
		}

		return (new Function1D(variables, cells, magnitudes));
	}

	/**
	 * Returns the product between this Function2D and another function. The
	 * multiplication is delegated to <code>Function1D.times(Function)</code> if
	 * the argument is a Function1D and they share a common Variable.
	 * 
	 * @param function
	 *            another factor
	 * @return the product between this Function1D and another function.
	 * @see Function1D#times(Function)
	 */
	@Override
	public final Function times(Function function) {
		if (function instanceof Function1D && contains(function._variables[0])) {
			return ((Function1D) function).times(this);
		} else if (function instanceof Function2D
				&& _x == function._variables[0] && _y == function._variables[1]) {
			Function result = this.clone();
			for (int i = 0; i < getDomainSize(); i++) {
				result._cells[i] *= function._cells[i];
			}
			return result;
		} else {
			return super.times(function);
		}
	}

	/**
	 * <p>
	 * Multiply this function by the argument function. Note that this function
	 * must contains the argument function in terms of the variables.
	 * </p>
	 * 
	 * @param function
	 *            multiplier function.
	 * @return the product between this function and the specified function.
	 */
	@Override
	public final void multiply(Function function) {
		if (function.getDimension() == 0) {
			multiply(function._cells[0]);
		} else if (function instanceof Function1D) {
			int xCard = _x.getCardinality();
			int yCard = _y.getCardinality();
			int index = 0;
			if (_x == ((Function1D) function)._x) {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						_cells[index] *= function._cells[i];
						index++;
					}
				}
			} else {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						_cells[index] *= function._cells[j];
						index++;
					}
				}
			}
		} else {
			for (int i = 0; i < getDomainSize(); i++) {
				_cells[i] *= function._cells[i];
			}
		}
	}

	/**
	 * <p>
	 * DIvide this function by the argument function. Note that this function
	 * must contains the argument function in terms of the variables. Also note
	 * that the argument function should contain NO zero cell at all.
	 * </p>
	 * 
	 * @param function
	 * @return the division
	 */
	@Override
	public final void divide(Function function) {
		if (function.getDimension() == 0) {
			divide(function._cells[0]);
		} else if (function instanceof Function1D) {
			int xCard = _x.getCardinality();
			int yCard = _y.getCardinality();
			int index = 0;
			if (_x == ((Function1D) function)._x) {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						_cells[index] /= function._cells[i];
						index++;
					}
				}
			} else {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						_cells[index] /= function._cells[j];
						index++;
					}
				}
			}
		} else {
			for (int i = 0; i < getDomainSize(); i++) {
				_cells[i] /= function._cells[i];
			}
		}
	}

}
