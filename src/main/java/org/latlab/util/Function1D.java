/**
 * Function1D.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.util;

//import hlcm.Function1V;

/**
 * This class provides an implementation for one-dimensional tabular functions,
 * namely, vectors.
 * 
 * @author Yi Wang
 * 
 */
public class Function1D extends Function {

	/**
	 * the shortcut to the only variable in this function.
	 */
	protected Variable _x;

	/**
	 * set f(-x = i) = val;
	 * 
	 * @param i
	 * @param val
	 */
	public void setCell(int i, double val) {
		_cells[i] = val;
	}

	/**
	 * "Superior" means that the variables contained in this function is a
	 * superset of those contained in the argument function. If so,
	 * this.multiply(function) can be called. If futher !function.hasZeroCell(),
	 * then this.divide(function) can be called.
	 * 
	 * @param function
	 * @return
	 */
	@Override
	public boolean superiorTo(Function function) {
		int dim = function.getDimension();
		if (dim == 0)
			return true;
		else if (dim == 1 && ((Function1D) function)._x == _x)
			return true;
		else
			return false;
	}

	/**
	 * This method will return the same 1-d function in the form of
	 * hlcm.Function1V. The variable of the Function1V is given.
	 */
//	public Function1V toFunction1V(hlcm.Variable var) {
//		Function1V fun_hlcm = new Function1V(var);
//		for (int i = 0; i < _x.getCardinality(); i++) {
//			fun_hlcm.setVal(i, _cells[i]);
//		}
//		return fun_hlcm;
//	}

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
	 *            array of variables to be involved. There is only one Variable.
	 */
	protected Function1D(Variable[] variables) {
		super(variables);

		_x = _variables[0];
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
	 *            array of variables in new function. There is only one
	 *            Variable.
	 * @param cells
	 *            array of cells in new function.
	 * @param magnitudes
	 *            array of magnitudes for variables in new function.
	 */
	protected Function1D(Variable[] variables, double[] cells, int[] magnitudes) {
		super(variables, cells, magnitudes);

		_x = _variables[0];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.latlab.util.Function#normalize(org.latlab.util.Variable)
	 */
	@Override
	public final boolean normalize(Variable variable) {

		// For Test
		// System.out.println("Function1D.normailize(Variable) executed");

		// argument variable must be the one involved in this function
		assert variable == _x;

		// reduces to normalization over all variable(s)
		return (normalize() == 0.0 ? true : false);
	}

	/*
	 * Note: I guess this method never used.
	 * 
	 * @see org.latlab.util.Function#project(org.latlab.util.Variable, int)
	 */
	@Override
	public final Function project(Variable variable, int state) {

		// For Test
		// System.out.println("Function1D.project(Variable, int) executed");

		// argument variable must be the one involved in this function
		assert variable == _x;

		// state must be valid
		assert variable.isValid(state);

		// result is a zero-dimensional function. the only cell is selected by
		// the argument state.
		Variable[] variables = new Variable[0];
		double[] cells = new double[] { _cells[state] };
		int[] magnitudes = new int[0];

		return (new Function(variables, cells, magnitudes));
	}

	/*
	 * Note: I guess this method never called in HLCM clique tree propogation.
	 * 
	 * @see org.latlab.util.Function#sumOut(org.latlab.util.Variable)
	 */
	@Override
	public final Function sumOut(Variable variable) {

		// For Test
		// System.out.println("Function1D.sumOut(Variable) executed");

		// argument variable must be the one involved in this function
		assert variable == _x;

		// result is a zero-dimensional function. the only cell contains the sum
		// of the cells in this function.
		Variable[] variables = new Variable[0];
		double[] cells = new double[] { sumUp() };
		int[] magnitudes = new int[0];

		return (new Function(variables, cells, magnitudes));
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
	public void multiply(Function function) {
		if (function.getDimension() == 0) {
			multiply(function._cells[0]);
		} else {
			for (int i = 0; i < getDomainSize(); i++) {
				_cells[i] *= function._cells[i];
			}
		}
	}

	/**
	 * <p>
	 * Divide this function by the argument function. Note that this function
	 * must contains the argument function in terms of the variables. Also note
	 * that when calling this method, the argument should contain NO zero cell
	 * at all.
	 * </p>
	 * 
	 * @param function
	 * @return the division
	 */
	@Override
	public void divide(Function function) {
		if (function.getDimension() == 0) {
			divide(function._cells[0]);
		} else {
			for (int i = 0; i < getDomainSize(); i++) {
				_cells[i] /= function._cells[i];
			}
		}
	}

	/**
	 * Returns the product between this function and the specified
	 * two-dimensional function.
	 * 
	 * @param function
	 *            two-dimensional multiplier function.
	 * @return the product between this function and the specified
	 *         two-dimensional function.
	 */
	@Override
	public final Function times(Function function) {

		if (function instanceof Function1D && function._variables[0] == _x) {
			Function result = this.clone();
			for (int i = 0; i < getDomainSize(); i++) {
				result._cells[i] *= function._cells[i];
			}
			return result;
		} else if (function instanceof Function2D && function.contains(_x)) {
			int length1 = function._variables.length;
			Variable[] variables = new Variable[length1];
			for (int i = 0; i < length1; i++)
				variables[i] = function._variables[i];

			double[] cells = new double[function.getDomainSize()];

			int index = 0;
			int xCard = ((Function2D) function)._x.getCardinality();
			int yCard = ((Function2D) function)._y.getCardinality();

			if (_x == ((Function2D) function)._x) {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						cells[index] = _cells[i] * function._cells[index];
						index++;
					}
				}
			} else {
				for (int i = 0; i < xCard; i++) {
					for (int j = 0; j < yCard; j++) {
						cells[index] = _cells[j] * function._cells[index];
						index++;
					}
				}
			}

			int length2 = function._magnitudes.length;
			int[] magnitudes = new int[length2];
			for (int i = 0; i < length2; i++)
				magnitudes[i] = function._magnitudes[i];

			return (new Function2D(variables, cells, magnitudes));
		} else {
			return super.times(function);
		}
	}
}
