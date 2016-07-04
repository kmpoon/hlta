/**
 * Function.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.latlab.util.DataSet.DataCase;

import cern.jet.random.Uniform;
import cern.jet.random.engine.MersenneTwister;

/**
 * <p>
 * This class provides an implementation for tabular functions.
 * </p>
 * 
 * <p>
 * Java does not natively support multi-dimensional array. Therefore, the main
 * technical issue here is how to implements multi-dimensional arrays. We choose
 * to simulate multi-dimensional arrays using one-dimensional arrays, and carry
 * out the indexing things by ourselves.
 * </p>
 * 
 * <p>
 * This class is time critical. It will be intensively used in the inference and
 * learning of BNs. So we use arrays instead of classes in
 * <code>Collection</code> framework for internal data structures. We also
 * specialize this class for one-dimensional and two-dimensional functions for
 * efficient manipulations. This can considerably speed up the inference and
 * learning of HLC models, which are the main application of this package.
 * </p>
 * 
 * @author Yi Wang
 * 
 */
public class Function implements Cloneable {

	// fast and high quality pseudo random number generator
	private static Uniform rndGenerator = new Uniform(new MersenneTwister(
			new Date()));

	/**
	 * Returns a function of the specified list of variables.
	 * 
	 * @param variables
	 *            list of variables to be involved.
	 * @return a function of the specified list of variables.
	 */
	public final static Function createFunction(List<Variable> variables) {
		Variable[] varArray = variables.toArray(new Variable[variables.size()]);

		// enforces order of variables
		Arrays.sort(varArray);

		return createFunction(varArray);
	}

	/**
	 * <p>
	 * Returns a function representation of the specified data set. The function
	 * will involve the same set of variables as the specified data set. The
	 * cell correspond to each data case will be filled with its weight. The
	 * others will be set to zero.
	 * </p>
	 * <p>
	 * When using this method, make sure that there is no missing values.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It will be intensively used in parameter
	 * estimation when we derive parameters from sufficient statistics.
	 * </p>
	 * 
	 * @param dataSet
	 *            data set to be converted to a function representation. Make
	 *            sure that this dataSet does <b>NOT</b> contain missing
	 *            values.
	 * @return a function representation of the specified data set.
	 */
	public final static Function createFunction(DataSet dataSet) {
		// missing values will confuse this conversion
		assert !dataSet.hasMissingValues();

		// passes deep copy so that the function is independent of the data set.
		// note that the variables in function and data set are in the same
		// order.
		Function f = Function.createFunction(dataSet.getVariables().clone());

		// fills in cells by going through all data cases
		for (DataCase dataCase : dataSet.getData()) {
			f._cells[f.computeIndex(dataCase.getStates())] = dataCase
					.getWeight();
		}

		return f;
	}

	/**
	 * Returns a function of the specified array of variables. Make sure the
	 * argument variables are in ascending order according to their birthday.
	 * <p>
	 * The method create Function1D or Function2D instance for the cases that
	 * there are only one or two Variables.
	 * </p>
	 * 
	 * @param variables
	 *            array of variables to be involved.
	 * @return a function of the specified array of variables.
	 */
	private final static Function createFunction(Variable[] variables) {
		Function f = null;

		// creates specialized function
		switch (variables.length) {

		case 1:
			f = new Function1D(variables);
			break;

		case 2:
			f = new Function2D(variables);
			break;

		default:
			f = new Function(variables);
			break;
		}

		return f;
	}

	/**
	 * Returns an identity function.
	 * 
	 * @return an identity function.
	 */
	public final static Function createIdentityFunction() {
		Function f = new Function(new Variable[] {});
		f._cells[0] = 1.0;

		return f;
	}

	/**
	 * Returns an identity function.
	 * 
	 * @return an identity function.
	 */
	public final static Function createIdentityFunction(Variable variable) {
		Function f = new Function1D(new Variable[] { variable });
		for (int i = 0; i < f._cells.length; i++) {
			f._cells[i] = 1.0;
		}

		return f;

	}

	/**
	 * Returns an indicator function that suppresses all cells except the one
	 * indicated by the arguments.
	 * 
	 * @param variables
	 *            list of variables to be involved.
	 * @param states
	 *            list of states that selects a cell.
	 * @return an indicator function that suppresses all cells except the one
	 *         indicated by the arguments.
	 */
	public final static Function createIndicatorFunction(
			ArrayList<Variable> variables, ArrayList<Integer> states) {
		Function f = createFunction(variables);
		f.setCell(variables, states, 1.0);

		return f;
	}

	/**
	 * Returns an indicator function that suppresses all cells except the one
	 * indicated by the arguments.
	 * 
	 * @param variable
	 *            variable to be involved.
	 * @param state
	 *            state that selects a cell.
	 * @return an indicator function that suppresses all cells except the one
	 *         indicated by the arguments. The return is actually an instance of
	 *         Function1D.
	 */
	public final static Function createIndicatorFunction(Variable variable,
			int state) {
		// state must be valid
		assert variable.isValid(state);

		Function f = new Function1D(new Variable[] { variable });
		f._cells[state] = 1.0;

		return f;
	}

	/**
	 * Returns a uniform distribution of the specified variable.
	 * 
	 * @param variable
	 *            variable to be involved.
	 * @return a uniform distribution of the specified variable. The return is
	 *         actually an instance of Function1D.
	 */
	public final static Function createUniformDistribution(Variable variable) {
		Function f = new Function1D(new Variable[] { variable });
		Arrays.fill(f._cells, 1.0 / variable.getCardinality());

		return f;
	}

	/**
	 * Returns a function of variable1 and variable2 so that f( variable1,
	 * variable2 ) =1 iff variable1 == variable2; otherwise f=0;
	 * 
	 * @param variable
	 *            variable to be involved.
	 * @return a uniform distribution of the specified variable. The return is
	 *         actually an instance of Function1D.
	 */
	public final static Function createDeterCondDistribution(
			Variable variable1, Variable variable2) {

		assert variable1.getCardinality() == variable2.getCardinality();

		ArrayList<Variable> variables = new ArrayList<Variable>(2);
		variables.add(variable1);
		variables.add(variable2);

		Function f = createFunction(variables);
		int cardinality = variable1.getCardinality();

		for (int index = 0; index < f.getDomainSize(); index = index
				+ cardinality + 1) {
			f._cells[index] = 1.0;
		}

		return f;
	}

	/**
	 * the array of variables involved in this function. we use array for fast
	 * and random access.
	 */
	protected Variable[] _variables;

	/**
	 * the array of magnitudes for variables of this function. this data
	 * structure is included for fast indexing. note that the first variable is
	 * at the most significant place.
	 */
	protected int[] _magnitudes;

	/**
	 * the one-dimensional array representation of the cells of this function.
	 * we use array for fast and random access.
	 */
	protected double[] _cells;

	/**
	 * <p>
	 * Constructs a function of the specified array of variables.
	 * </p>
	 * <p>
	 * Note: Only function classes are supposed to call this method.
	 * <ul>
	 * <li> When using this method, be aware of that the method <b>just</b>
	 * constructs an exact Function instance regardless of the number of
	 * variables involved.
	 * <li> The argument array of Variables must be sorted.
	 * </ul>
	 * </p>
	 * 
	 * @param variables
	 *            array of variables to be involved.
	 */
	protected Function(Variable[] variables) {
		_variables = variables;

		// builds magnitude array with _variables[0] at most significant bit
		_magnitudes = new int[getDimension()];

		int magnitude = 1;
		for (int i = getDimension() - 1; i >= 0; i--) {
			_magnitudes[i] = magnitude;
			magnitude *= _variables[i].getCardinality();
		}

		// magnitude equals domain size at the end
		_cells = new double[magnitude];
	}

	/**
	 * <p>
	 * Constructs a function with all its internal data structures specified.
	 * </p>
	 * <p>
	 * Note: Only function classes are supposed to call this method.
	 * <ul>
	 * <li> When using this method, be aware of that the method <b>just</b>
	 * constructs an exact Function instance regardless of the number of
	 * variables involved.
	 * <li> The argument array of Variables must be sorted.
	 * </ul>
	 * </p> *
	 * 
	 * @param variables
	 *            array of variables in new function. They must be sorted.
	 * @param cells
	 *            array of cells in new function.
	 * @param magnitudes
	 *            array of magnitudes for variables in new function.
	 */
	protected Function(Variable[] variables, double[] cells, int[] magnitudes) {
		_variables = variables;
		_cells = cells;
		_magnitudes = magnitudes;
	}

	/**
	 * <p>
	 * Returns a function that involves one more variable than this function.
	 * </p>
	 * 
	 * <p>
	 * Let f and g be this function and the new function to be created,
	 * respectively. Also, let X and Y be the variables involved in f and the
	 * new variable to be involved in g, respectively. We will set the cells of
	 * g such that g(X, y) = f(X). In case that the caller is a Zero-dimensional
	 * Function, the output is then an instance of Function1D and every cell has
	 * the same value as the original unique cell in the Zero-dimensional
	 * function.
	 * </p>
	 * 
	 * @param variable
	 *            new variable to be involved.
	 * @return a function that involves one more variable than this function.
	 */
	public final Function addVariable(Variable variable) {
		// variable name must be unique in new function
		assert !contains(variable.getName());

		int cardinality = variable.getCardinality();
		int newDimension = getDimension() + 1;
		int newDomainSize = getDomainSize() * cardinality;

		Variable[] variables = new Variable[newDimension];
		double[] cells = new double[newDomainSize];
		int[] magnitudes = new int[newDimension];

		// Handle a special case that the caller function is Zero-dimensional
		if (newDimension == 1) {
			for (int i = 0; i < cells.length; i++) {
				cells[i] = _cells[0];
			}
			variables[0] = variable;
			magnitudes[0] = 1;
			return new Function1D(variables, cells, magnitudes);
		}

		int index = -(1 + Arrays.binarySearch(_variables, variable));

		variables[index] = variable;
		System.arraycopy(_variables, 0, variables, 0, index);
		System.arraycopy(_variables, index, variables, index + 1, newDimension
				- index - 1);

		System.arraycopy(_magnitudes, index, magnitudes, index + 1,
				newDimension - index - 1);
		for (int i = 0; i < index; i++) {
			magnitudes[i] = _magnitudes[i] * cardinality;
		}
		magnitudes[index] = index == 0 ? _magnitudes[0]
				* (_variables[0].getCardinality()) : _magnitudes[index - 1];

		int divisor = magnitudes[index];
		int unit = magnitudes[index] * cardinality;
		int exceptResidual = 0;
		for (int i = 0, residual = 0; i < getDomainSize(); i++, residual++) {
			if (residual >= divisor) {
				residual -= divisor;
				exceptResidual += unit;
			}
			// This index i in the original function maps to
			// (exceptResidual+residual) in the new function with newly added
			// variable = 0;
			int initialIndex = exceptResidual + residual;
			for (int j = 0; j < cardinality; j++) {
				cells[initialIndex] = _cells[i];
				initialIndex += divisor;
			}
		}
		return createFunction(variables, cells, magnitudes);
	}

	/**
	 * Creates and returns a deep copy of this function. This implementation
	 * copies everything in this function but reuses the reference to each
	 * variable it involves.
	 * 
	 * @return a deep copy of this function.
	 */
	public Function clone() {
		// My experience: Don't call clone for variables, cells and magnitudes.
		// It is too expensive.
		int length1 = _variables.length;
		Variable[] variables = new Variable[length1];
		for (int i = 0; i < length1; i++)
			variables[i] = _variables[i];

		int length2 = _cells.length;
		double[] cells = new double[length2];
		for (int i = 0; i < length2; i++)
			cells[i] = _cells[i];

		int length3 = _magnitudes.length;
		int[] magnitudes = new int[length3];
		for (int i = 0; i < length3; i++)
			magnitudes[i] = _magnitudes[i];

		return createFunction(variables, cells, magnitudes);
	}

	/**
	 * <p>
	 * Returns the index of the cell in the internal one-dimensional array that
	 * is specified by the array of states taken by the variables.
	 * </p>
	 * <p>
	 * Note: Make sure the method is used for compute index for valid states
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in
	 * <code>Function.times(Function)</code>.
	 * </p>
	 * 
	 * @param states
	 *            array of states that specifies a cell.
	 * @return the index of the cell in the internal one-dimensional array.
	 * @see times(Function)
	 */
	private final int computeIndex(int[] states) {
		int index = 0;

		int dimension = getDimension();
		for (int i = 0; i < dimension; i++) {
			index += (states[i] * _magnitudes[i]);
		}

		return index;
	}

	/**
	 * <p>
	 * Computes the array of states taken by the variables that corresponds to
	 * the cell specified by the index in the internal one-dimensional array.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in
	 * <code>Function.times(Function)</code>.
	 * </p>
	 * 
	 * @param index
	 *            index in the internal one-dimensional array that specifies a
	 *            cell.
	 * @param states
	 *            array of states that corresponds to the cell.
	 * @see times(Function)
	 */
	public final void computeStates(int index, int[] states) {
		int dimension = getDimension();
		for (int i = 0; i < dimension; i++) {
			if (index == 0) {
				// states of remaining variables are all zeros
				Arrays.fill(states, i, dimension, 0);

				return;
			} else {
				states[i] = index / _magnitudes[i];
				index -= (states[i] * _magnitudes[i]);
			}
		}
	}

	/**
	 * Replace the var with a new Variable newVar according to the following
	 * rule: "var" is a original variable in this function. The funtion will not
	 * contains the variable "var" but the variable "nbewVar". The newVar has
	 * one less state than the var, that is |newVar|=|var|-1. And: (1)
	 * f_new(...,newVar=s_i...)=f(...,newVar=s_i...)+f(...,newVar=s_j...)...
	 * 
	 * @param var
	 *            The original var in this Function
	 * @param i
	 *            The first state to be combined
	 * @param j
	 *            The second state to be combined
	 * @param newVar
	 *            New variable introduced to replace var.
	 * @return
	 */
	public Function combine(Variable var, int si, int sj, Variable newVar) {

		assert contains(var);
		assert var.isValid(si) && var.isValid(sj);

		int dimension = getDimension();

		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);

		int[] hStates = new int[dimension];
		int[] fStates1 = new int[dimension];
		int[] fStates2 = new int[dimension];
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates1[fIndex] = hStates[hIndex];
				fStates2[fIndex] = hStates[hIndex];
			}
			// Deal with the variable whose states are merged.
			int s = hStates[indexNewVar];
			if (s == si) {
				fStates1[indexVar] = si;
				fStates2[indexVar] = sj;
				h._cells[i] = _cells[computeIndex(fStates1)]
						+ _cells[computeIndex(fStates2)];
			} else if (s < sj) {
				fStates1[indexVar] = s;
				h._cells[i] = _cells[computeIndex(fStates1)];
			} else {
				fStates1[indexVar] = s + 1;
				h._cells[i] = _cells[computeIndex(fStates1)];
			}
		}
		return h;
	}
	
	/**
	 * 
	 * similar to the above function. However, it can merge more than two states at one time.
	 * 
	 * @param var
	 *        
	 * @param statesToMerge
	 *        all the states that will be merged. The states are ordered in ascending order.
	 * @param newVar
	 *        the new variable after merge only has two states.
	 *        
	 * Note: this function is only used in AuxiliaryInfoDialog.java in Lantern. 
	 * @return
	 */
	public Function combine(Variable var, int[] statesToMerge, int stateToKeep, Variable newVar) {

		int dimension = getDimension();

		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);
		int minState = statesToMerge[0];
		
		int[] hStates = new int[dimension];
		int[] fStates = new int[dimension];
		
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates[fIndex] = hStates[hIndex];
			}
			
			// Deal with the variable whose states are merged.
			int s = hStates[indexNewVar];
			if (s == minState) {
				for(int states : statesToMerge){
					fStates[indexVar] = states;
					h._cells[i] += _cells[computeIndex(fStates)];
				}
			} 
			else if (s < minState) {
				fStates[indexVar] = s;
				h._cells[i] = _cells[computeIndex(fStates)];
			} 
			else {
				fStates[indexVar] = stateToKeep;
				h._cells[i] = _cells[computeIndex(fStates)];
			}
		}
		return h;
	}

	/**
	 * Replace the var with a new Variable newVar according to the following
	 * rule...
	 * 
	 * @param var
	 *            The original var in this Function
	 * @param i
	 *            The first state to be combined
	 * @param j
	 *            The second state to be combined
	 * @param newVar
	 *            New variable introduced to replace var.
	 * @return
	 */
	public Function averageCombine(Variable var, int si, int sj, Variable newVar) {

		assert contains(var);
		assert var.isValid(si) && var.isValid(sj);

		int dimension = getDimension();

		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);

		int[] hStates = new int[dimension];
		int[] fStates1 = new int[dimension];
		int[] fStates2 = new int[dimension];
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates1[fIndex] = hStates[hIndex];
				fStates2[fIndex] = hStates[hIndex];
			}
			// Deal with the variable whose states are merged.
			int s = hStates[indexNewVar];
			if (s == si) {
				fStates1[indexVar] = si;
				fStates2[indexVar] = sj;
				h._cells[i] = (_cells[computeIndex(fStates1)] + _cells[computeIndex(fStates2)]) / 2;
			} else if (s < sj) {
				fStates1[indexVar] = s;
				h._cells[i] = _cells[computeIndex(fStates1)];
			} else {
				fStates1[indexVar] = s + 1;
				h._cells[i] = _cells[computeIndex(fStates1)];
			}
		}
		return h;
	}

	/**
	 * 
	 * @param var
	 *            The original variable in this function
	 * @param s
	 *            The state to be splited
	 * @param newVar
	 *            We use the new variable to take the place of var. Cardinality
	 *            is increased by one.
	 * @return A new Function through split the state s of Variable var.
	 */
	public Function split(Variable var, int s, Variable newVar) {

		assert contains(var);
		assert var.isValid(s);

		int dimension = getDimension();

		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);

		int[] hStates = new int[dimension];
		int[] fStates = new int[dimension];
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates[fIndex] = hStates[hIndex];
			}
			// Deal with the variable whose states are merged.
			int state = hStates[indexNewVar];
			if (state == s || state == newVar.getCardinality() - 1) {
				fStates[indexVar] = s;
				h._cells[i] = _cells[computeIndex(fStates)] / 2;
			} else {
				fStates[indexVar] = state;
				h._cells[i] = _cells[computeIndex(fStates)];
			}
		}
		return h;
	}

	/**
	 * 
	 * @param var
	 *            The original variable in this function
	 * @param s
	 *            The state to be splited
	 * @param newVar
	 *            We use the new variable to take the place of var. Cardinality
	 *            is increased by one.
	 * @return A new Function through split the state s of Variable var.
	 */
	public Function stateCopy(Variable var, int s, Variable newVar) {

		assert contains(var);
		assert var.isValid(s);

		int dimension = getDimension();

		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);

		int[] hStates = new int[dimension];
		int[] fStates = new int[dimension];
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates[fIndex] = hStates[hIndex];
			}
			// Deal with the variable whose states are merged.
			int state = hStates[indexNewVar];
			if (state == newVar.getCardinality() - 1) {
				fStates[indexVar] = s;
				h._cells[i] = _cells[computeIndex(fStates)];
			} else {
				fStates[indexVar] = state;
				h._cells[i] = _cells[computeIndex(fStates)];
			}
		}
		return h;
	}

	/**
	 * 
	 * Return a new function in which the latVar is replaced by the newVar.
	 * Values are keep the same.
	 * 
	 * @param var
	 * @param newVar
	 * @return
	 */
	public Function replaceVar(Variable var, Variable newVar) {
		assert contains(var);

		int dimension = getDimension();
		int indexVar = indexOf(var);

		ArrayList<Variable> variables = new ArrayList<Variable>(dimension);

		for (int i = 0; i < dimension; i++) {
			if (_variables[i] != var)
				variables.add(_variables[i]);
		}
		variables.add(newVar);

		Function h = createFunction(variables);
		int indexNewVar = h.indexOf(newVar);
		
		int[] hStates = new int[dimension];
		int[] fStates = new int[dimension];
		for (int i = 0; i < h.getDomainSize(); i++) {
			h.computeStates(i, hStates);
			// Copy unchanged Variables
			for (int hIndex = 0, fIndex = 0; hIndex < dimension
					&& fIndex < dimension; hIndex++, fIndex++) {
				if (hIndex == indexNewVar)
					hIndex++;
				if (fIndex == indexVar)
					fIndex++;
				if (hIndex >= dimension || fIndex >= dimension)
					break;
				fStates[fIndex] = hStates[hIndex];
			}
			// Deal with the new variable.
			int state = hStates[indexNewVar];
			fStates[indexVar] = state;
			h._cells[i] = _cells[computeIndex(fStates)];
		}
		return h;
	}

	/**
	 * Returns <code>true</code> if this function involves a variable with the
	 * specified name.
	 * 
	 * @param name
	 *            name whose presence in this function is to be tested.
	 * @return <code>true</code> if a variable with the specified name is
	 *         present.
	 */
	public final boolean contains(String name) {

		name = name.trim();

		for (Variable variable : _variables) {
			if (variable.getName().equals(name)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns <code>true</code> if this function involves the specified
	 * variable.
	 * 
	 * @param variable
	 *            variable whose presence in this function is to be tested.
	 * @return <code>true</code> if the specified variable is present.
	 */
	public final boolean contains(Variable variable) {
		return (indexOf(variable) < 0 ? false : true);
	}

	/**
	 * Returns <code>true</code> if this function contains zero cell. This
	 * method will be used to decide whether the function can be a dividend.
	 * 
	 * @return <code>true</code> if this function contains zero cell;
	 *         <code>false</code>, otherwise.
	 */
	public boolean containsZeroCell() {
		for (int i = 0; i < getDomainSize(); i++) {
			if (_cells[i] == 0.0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns <code>true</code> if this function involves the specified
	 * collection of variables.
	 * 
	 * @param variables
	 *            collection of variables whose presence in this function are to
	 *            be tested.
	 * @return <code>true</code> if the specified collection of variables are
	 *         present.
	 */
	public final boolean containsAll(Collection<Variable> variables) {
		for (Variable variable : variables) {
			if (!contains(variable)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns a function with all its internal data structures specified.
	 * 
	 * @param variables
	 *            array of variables in new function.
	 * @param cells
	 *            array of cells in new function.
	 * @param magnitudes
	 *            array of magnitudes for variables in new function.
	 * @return a function with all its internal data structures specified.
	 */
	private final Function createFunction(Variable[] variables, double[] cells,
			int[] magnitudes) {
		Function f = null;

		// creates specialized function
		switch (variables.length) {

		case 1:
			f = new Function1D(variables, cells, magnitudes);
			break;

		case 2:
			f = new Function2D(variables, cells, magnitudes);
			break;

		default:
			f = new Function(variables, cells, magnitudes);
			break;
		}

		return f;
	}

	/**
	 * Replaces each cell in this function with e to the power of this cell.
	 * <p>
	 * Seems not used.
	 * </p>
	 */
	public final void exp() {
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			_cells[i] = Math.exp(_cells[i]);
		}
	}

	/**
	 * Returns an one-dimensional array representation of cells in this
	 * function. Make sure you understand the meaning of this representation
	 * before you invoke this function.
	 * 
	 * @return an one-dimensional array representation of cells in this
	 *         function.
	 */
	public final double[] getCells() {
		return _cells;
	}

	/**
	 * Returns an one-dimensional array representation of cells in this
	 * function. The specified list of variables defines the order of cells. The
	 * first variable is at the most significant place.
	 * <p>
	 * Note: So far the only use of this method is to save(write) the cpt of a
	 * BeliefNode. Therefore this implemenation can be not efficient.
	 * </p>
	 * 
	 * @param variables
	 *            list of variables defines the order of cells in the
	 *            one-dimensional array representation.
	 * @return an one-dimensional array representation of cells in this
	 *         function.
	 */
	public final double[] getCells(List<Variable> variables) {
		int dimension = getDimension();
		int domainSize = getDomainSize();

		// argument variables should be exactly what this function involes
		assert containsAll(variables) && dimension == variables.size();

		// maps from argument variables to internal array
		int[] map = new int[dimension];
		for (int i = 0; i < dimension; i++) {
			map[i] = indexOf(variables.get(i));
		}

		double[] cells = new double[domainSize];

		// we play a trick here. the argument variables are not necessary
		// sorted, but we builds function of them for indexing.
		Function f = new Function(variables.toArray(new Variable[variables
				.size()]));

		int[] internalStates = new int[dimension];
		int[] externalStates = new int[dimension];

		// fills in one-dimensional array for cells
		for (int i = 0; i < domainSize; i++) {
			// one-dimensional index to multi-dimensional ones
			f.computeStates(i, externalStates);

			// external indices to internal ones
			for (int j = 0; j < dimension; j++) {
				internalStates[map[j]] = externalStates[j];
			}

			// multi-dimensional indices to one-dimensional one
			cells[i] = _cells[computeIndex(internalStates)];
		}

		return cells;
	}

	/**
	 * Returns the dimension this function. The dimension equals to the number
	 * of variables involved in this function.
	 * 
	 * @return the dimension this function.
	 */
	public final int getDimension() {
		return _variables.length;
	}

	/**
	 * Returns the domain size of this function. The domain size equals to the
	 * number of cells in this function.
	 * 
	 * @return the domain size of this function.
	 */
	public final int getDomainSize() {
		return _cells.length;
	}

	/**
	 * Returns the index of the specified variable in this function.
	 * 
	 * @param variable
	 *            variable whose index it be returned.
	 * @return the index of the specified variable in this function.
	 */
	private final int indexOf(Variable variable) {
		// binary search for effiency: that is one reason why we sort variables
		return Arrays.binarySearch(_variables, variable);
	}

	/**
	 * Replaces each cell in this function with its natural logarithm.
	 * <p>
	 * log(0) set to 0
	 * </p>
	 */
	public final void log() {
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			if (_cells[i] > 0.0)
				_cells[i] = Math.log(_cells[i]);
		}
	}

	/**
	 * <p>
	 * Returns the marginal function of the specified collection of variables
	 * derived from this function.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is called by
	 * <code>CliqueTreePropagation.computeBelief(java.util.Collection)</code>
	 * and <code>CliqueTreePropagation.computeFamilyBelief(BeliefNode)</code>.
	 * Therefore, it will be intensively used in parameter estimation which
	 * relies on the aforementioned methods.
	 * </p>
	 * TODO
	 * 
	 * @param variables
	 *            collection of variables to be retained in the marginal
	 *            function.
	 * @return the marginal function of the specified collection of variables.
	 * @see org.east.reasoner.CliqueTreePropagation#computeBelief(Collection)
	 * @see org.east.reasoner.CliqueTreePropagation#computeFamilyBelief(org.latlab.model.BeliefNode)
	 */
	public final Function marginalize(Collection<Variable> variables) {
		// argument variables must be involved in this function
		assert containsAll(variables);

		if (variables.size() == getDimension()) {
			// retains all variables
			return clone();
		}

		Function f = this;

		for (Variable x : _variables) {
			if (!variables.contains(x)) {
				f = f.sumOut(x);
			}
		}

		return f;
	}

	/**
	 * Returns the marginal function of the specified variable derived from this
	 * function.
	 * 
	 * @param variable
	 *            variable to be retained in the marginal function.
	 * @return the marginal function of the specified variable.
	 */
	public final Function marginalize(Variable variable) {
		int index = indexOf(variable);

		// argument variable must be involved in this function
		assert index >= 0;

		if (getDimension() == 1) {
			// retains the only variable
			return clone();
		}

		Function f = this;

		// sums out variables before argument
		for (int i = 0; i < index; i++) {
			f = f.sumOut(_variables[i]);
		}

		// sums out variables after argument
		for (int i = index + 1; i < getDimension(); i++) {
			f = f.sumOut(_variables[i]);
		}

		return f;
	}

	/**
	 * Returns the minimum cell in this function.
	 * 
	 * @return the minimum cell in this function.
	 */
	public final double min() {
		double minCell = Double.POSITIVE_INFINITY;
		for (double cell : _cells) {
			minCell = Math.min(minCell, cell);
		}

		return minCell;
	}

	/**
	 * <p>
	 * Normalizes this function such that its cells sum up to one, and returns
	 * the normalizing constant. In case that the normalizing constant is zero,
	 * we makes this function a uniform distribution.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in inference
	 * algorithms.
	 * </p>
	 * 
	 * @return the normalizing constant.
	 */
	public final double normalize() {
		double sum = sumUp();

		if (sum != 0.0) {
			divide(sum);
		} else {
			// uniformly distributes it if normalizing constant equals 0
			Arrays.fill(_cells, 1.0 / getDomainSize());
		}

		return sum;
	}

	/**
	 * <p>
	 * We suppose the argument Variable, denoted by Y, is in the function. The
	 * other variables are denoted by X and the function is f(X,Y). The method
	 * normalizes this function with respect to Y in the sense that for a cell
	 * f(X=a,Y=b), a new value f(X=a,Y=b)/\sum{Y}f(X=a,Y) is computed and set.
	 * This method will return <code>true</code> if for some X=a,
	 * \sum{Y}f(X=a,Y)=0. And we will set f(X=a,Y=b)=1/|Y| for any b, /i.e. an
	 * uniform distribution.
	 * </p>
	 * <p>
	 * For a more specific example, we can image the original function is a
	 * joint probablity P(X,Y). call narmalize(Y) will turn out the conditional
	 * probability P(Y|X). If there is a X=a such that P(X=a)=0, P(Y|X=a) is
	 * uniform then.
	 * </p>
	 * <p>
	 * This method is time critical. It is intensively used in parameter
	 * estimation. So we specialize this method.
	 * </p>
	 * 
	 * 
	 * @param variable
	 *            variable with respect to which this function is to be
	 *            normalized.
	 * @return <code>true</code> if some normalizing constant is zero.
	 */
	public boolean normalize(Variable variable) {
		boolean hasZeroSum = false;

		int variableIndex = indexOf(variable);

		// argument variable must be involved in this function
		assert variableIndex >= 0;

		int cardinality = variable.getCardinality();
		int subdomainSize = getDomainSize() / cardinality;

		// we have the original domain and the subdomain without the normalizing
		// variable. the main issue here is how to efficiently traverse the
		// subdomain and map it back to the original domain. the idea is as
		// follows: we simply go through the one-dimensional array
		// representation for the subdomain. meanwhile, we go through the
		// one-dimensional array representation of the original domain by
		// simulating the carrying in process.
		int magnitude = _magnitudes[variableIndex];
		int magnitude2 = magnitude * cardinality;
		int carry = 0;
		int residual = 0;

		int[] affectedCells = new int[cardinality];
		double uniform = 1.0 / cardinality;

		for (int i = 0; i < subdomainSize; i++) {
			// computes the index in the original domain
			int index = carry + residual;

			// computes normalizing constant
			double sum = 0.0;
			for (int j = 0; j < cardinality; j++) {
				sum += _cells[index];
				affectedCells[j] = index;
				index += magnitude;
			}

			// normalizes
			if (sum != 0.0) {
				for (int j = 0; j < cardinality; j++) {
					_cells[affectedCells[j]] /= sum;
				}
			} else {
				for (int j = 0; j < cardinality; j++) {
					_cells[affectedCells[j]] = uniform;
				}

				hasZeroSum = true;
			}

			// next element in original domain
			residual++;

			if (residual == magnitude) {
				// carries in
				carry += magnitude2;
				residual = 0;
			}
		}

		return hasZeroSum;
	}

	/**
	 * <p>
	 * Increases the cell of this function by the corresponding cell of the
	 * specified function.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in parameter
	 * estimation for updating sufficient statistics.
	 * </p>
	 * 
	 * @param function
	 *            addend function.
	 */
	public final void plus(Function function) {
		// two functions must involve the same set of variables
		assert Arrays.equals(_variables, function._variables);

		// variables in two functions are both in order of their birthdays. so
		// simply adds up two one-dimensional arrays of cells.
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			_cells[i] += function._cells[i];
		}
	}

	/**
	 * <p>
	 * Returns a function that is a projection of this function specified by the
	 * arguments.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in inference
	 * algorithms for absorbing evidence.
	 * </p>
	 * 
	 * @param variable
	 *            variable to be instantiated.
	 * @param state
	 *            state of the variable.
	 */
	public Function project(Variable variable, int state) {
		int variableIndex = indexOf(variable);

		// argument variable must be involved in this function
		assert variableIndex >= 0;

		// state must be valid
		assert variable.isValid(state);

		int cardinality = variable.getCardinality();
		int newDimension = getDimension() - 1;
		int newDomainSize = getDomainSize() / cardinality;

		double[] cells = new double[newDomainSize];

		// we have the original domain and the subdomain without the projecting
		// variable. the main issue here is how to efficiently traverse the
		// subdomain and map it back to the original domain. the idea is as
		// follows: we simply go through the one-dimensional array
		// representation for the subdomain. meanwhile, we go through the
		// one-dimensional array representation of the original domain by
		// simulating the carrying in process.
		int magnitude = _magnitudes[variableIndex];
		int magnitude2 = magnitude * cardinality;

		// note that the carry term aborbs the bias introduced by the state
		int carry = state * magnitude;
		int residual = 0;

		for (int i = 0; i < newDomainSize; i++) {
			// instantiates
			cells[i] = _cells[carry + residual];

			// next element in original domain
			residual++;

			if (residual == magnitude) {
				// carries in
				carry += magnitude2;
				residual = 0;
			}
		}

		// fields for new function
		Variable[] variables = new Variable[newDimension];
		System.arraycopy(_variables, 0, variables, 0, variableIndex);
		System.arraycopy(_variables, variableIndex + 1, variables,
				variableIndex, newDimension - variableIndex);

		int[] magnitudes = new int[newDimension];
		System.arraycopy(_magnitudes, variableIndex + 1, magnitudes,
				variableIndex, newDimension - variableIndex);

		// scales down the magnitudes for X1, X2, ..., X(k-1) by |Xk|
		for (int i = 0; i < variableIndex; i++) {
			magnitudes[i] = _magnitudes[i] / cardinality;
		}

		return createFunction(variables, cells, magnitudes);
	}

	/**
	 * Returns a function that is a projection of this function. It is obtained
	 * by instantiating the specified variables.
	 * 
	 * This is a naive implementation which instantiates the specified variables
	 * one by one. More sophisticated implementation is required for the sake of
	 * efficiency. TODO
	 * 
	 * @param vars
	 *            variables to be instantiated.
	 * @param states
	 *            states of the variables.
	 * @return a function that is a projection of this function specified by the
	 *         arguments.
	 */
	public Function project(ArrayList<Variable> vars, ArrayList<Integer> states) {
		// variables must match states
		assert vars.size() == states.size();

		// repeat projection
		Function f = this;
		for (int i = 0; i < vars.size(); i++) {
			f = f.project(vars.get(i), states.get(i));
		}

		return f;
	}

	/**
	 * Makes this function a collection of random distributions of the specified
	 * variable.
	 */
	public final void randomlyDistribute(Variable variable) {
		// randomly sets cells within (0.0, 1.0)
		// I try to avoid likelihood = 0 by excluding zero.
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) 
		{
			double rand = -1; 
			
			while(rand <= 0)
			{
				rand = Math.random();
			}
			
			_cells[i] = 1.0 - rand;
		}

		// enforces distribution constraint
		normalize(variable);
	}

	/**
	 * Returns a function with the specified variable from this function. Let f
	 * and g be this function and the new one, respectively. Let X and Y be the
	 * variables involved in this function and the variable to be removed. We
	 * set the cells of g such that g(X\Y) = f(X\Y, Y = 0).
	 * 
	 * @param variable
	 *            variable to be removed.
	 * @return a function with the specified variable from this function.
	 */
	public final Function removeVariable(Variable variable) {
		return project(variable, 0);
	}

	/**
	 * Samples from the specified single-variate distribution.
	 * 
	 * @return a sample from the specified single-variate distribution.
	 */
	public int sample() {
		// can only sample from single-variate function
		assert getDimension() == 1;

		// ensure the distribution sum up to one
		assert sumUp() == 1.0;

		// randomly generate a double within (0, 1)
		double rand = rndGenerator.nextDouble();

		// check which segment the random number lies in
		double accum = 0.0;
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize - 1; i++) {
			accum += _cells[i];

			if (rand < accum) {
				return i;
			}
		}

		return domainSize - 1;
	}

	/**
	 * Updates the cell indicated by the arguments.
	 * 
	 * @param variables
	 *            list of variables that are involved. These must be all
	 *            variables contained in this function. Variables in this
	 *            ArrayList need not to be sorted.
	 * @param states
	 *            list of states that selects a cell.
	 * @param cell
	 *            new value of the cell.
	 */
	public final void setCell(ArrayList<Variable> variables,
			ArrayList<Integer> states, double cell) {
		int dimension = getDimension();

		// argument variables should be exactly what this function involes
		assert containsAll(variables) && dimension == variables.size()
				&& dimension == states.size();

		// maps from argument variables to internal array
		int[] map = new int[dimension];
		for (int i = 0; i < dimension; i++) {
			// state must be valid
			assert variables.get(i).isValid(states.get(i));

			map[i] = indexOf(variables.get(i));
		}

		int[] internalStates = new int[dimension];
		for (int i = 0; i < dimension; i++) {
			internalStates[map[i]] = states.get(i);
		}

		// System.out.println( " " + cell);
		_cells[computeIndex(internalStates)] = cell;
	}

	/**
	 * Updates the cells in this function using the specified one-dimensional
	 * array representation. The specified list of variables defines the order
	 * of cells. The first variable is at the most significant place.
	 * 
	 * @param variables
	 *            An ArrayList containing all variables in this function. These
	 *            Variables are not necessarily in ascending order. It in nature
	 *            defines the order of cells in the one-dimensional array
	 *            representation.
	 * @param cells
	 *            new values of cells.
	 */
	public final void setCells(ArrayList<Variable> variables,
			ArrayList<Double> cells) {
		int dimension = getDimension();
		int domainSize = getDomainSize();

		// argument variables should be exactly what this function involes
		assert containsAll(variables) && dimension == variables.size()
				&& domainSize == cells.size();

		// maps from argument variables to internal array
		int[] map = new int[dimension];
		for (int i = 0; i < dimension; i++) {
			map[i] = indexOf(variables.get(i));
		}

		// we play a trick here. the argument variables are not necessary
		// sorted, but we builds function of them for indexing.
		Function f = new Function(variables.toArray(new Variable[variables
				.size()]));

		int[] internalStates = new int[dimension];
		int[] externalStates = new int[dimension];
		for (int i = 0; i < domainSize; i++) {
			// one-dimensional index to multi-dimensional ones
			f.computeStates(i, externalStates);

			// external indices to internal ones
			for (int j = 0; j < dimension; j++) {
				internalStates[map[j]] = externalStates[j];
			}

			// multi-dimensional indices to one-dimensional one
			_cells[computeIndex(internalStates)] = cells.get(i);
		}
	}

	/**
	 * Another version of setCells
	 */
	public final void setCells(ArrayList<Variable> variables,
			double[] cells) {
		int dimension = getDimension();
		int domainSize = getDomainSize();

		// argument variables should be exactly what this function involes
		assert containsAll(variables) && dimension == variables.size()
				&& domainSize == cells.length;

		// maps from argument variables to internal array
		int[] map = new int[dimension];
		for (int i = 0; i < dimension; i++) {
			map[i] = indexOf(variables.get(i));
		}

		// we play a trick here. the argument variables are not necessary
		// sorted, but we builds function of them for indexing.
		Function f = new Function(variables.toArray(new Variable[variables
				.size()]));

		int[] internalStates = new int[dimension];
		int[] externalStates = new int[dimension];
		for (int i = 0; i < domainSize; i++) {
			// one-dimensional index to multi-dimensional ones
			f.computeStates(i, externalStates);

			// external indices to internal ones
			for (int j = 0; j < dimension; j++) {
				internalStates[map[j]] = externalStates[j];
			}

			// multi-dimensional indices to one-dimensional one
			_cells[computeIndex(internalStates)] = cells[i];
		}
	}

	
	/**
	 * Returns a function with the specified variable summed out from this
	 * function.
	 * 
	 * @param variable
	 *            variable to be summed out.
	 * @return a function with the specified variable summed out
	 */
	public Function sumOut(Variable variable) {

		int variableIndex = indexOf(variable);

		// argument variable must be involved in this function
		assert variableIndex >= 0;

		int cardinality = variable.getCardinality();
		int newDimension = getDimension() - 1;
		int newDomainSize = getDomainSize() / cardinality;

		double[] cells = new double[newDomainSize];

		// we have the original domain and the subdomain without the normalizing
		// variable. the main issue here is how to efficiently traverse the
		// subdomain and map it back to the original domain. the idea is as
		// follows: we simply go through the one-dimensional array
		// representation for the subdomain. meanwhile, we go through the
		// one-dimensional array representation of the original domain by
		// simulating the carrying in process.
		int magnitude = _magnitudes[variableIndex];
		int magnitude2 = magnitude * cardinality;
		int carry = 0;
		int residual = 0;

		for (int i = 0; i < newDomainSize; i++) {
			// computes the index
			int index = carry + residual;

			// computes sum
			for (int j = 0; j < cardinality; j++) {
				cells[i] += _cells[index];
				index += magnitude;
			}

			// next element in original domain
			residual++;

			if (residual == magnitude) {
				// carries in
				carry += magnitude2;
				residual = 0;
			}
		}

		// fields for new function
		Variable[] variables = new Variable[newDimension];
		System.arraycopy(_variables, 0, variables, 0, variableIndex);
		System.arraycopy(_variables, variableIndex + 1, variables,
				variableIndex, newDimension - variableIndex);

		int[] magnitudes = new int[newDimension];
		System.arraycopy(_magnitudes, variableIndex + 1, magnitudes,
				variableIndex, newDimension - variableIndex);

		// scales down the magnitudes for X1, X2, ..., X(k-1) by |Xk|
		for (int i = 0; i < variableIndex; i++) {
			magnitudes[i] = _magnitudes[i] / cardinality;
		}

		return createFunction(variables, cells, magnitudes);
	}

	/**
	 * <p>
	 * Returns the sum of the cells in this function.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in inference
	 * algorithms to compute likelihood.
	 * </p>
	 * 
	 * @return the sum of the cells in this function.
	 */
	public final double sumUp() {
		double sum = 0.0;

		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			sum += _cells[i];
		}

		return sum;
	}

	/**
	 * <p>
	 * Scales up the cells in this function by the specified constant.
	 * </p>
	 * 
	 * @param constant
	 *            constant by which the cells are to be scaled up.
	 */
	public void multiply(double constant) {
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			_cells[i] *= constant;
		}
	}

	/**
	 * <p>
	 * Scales down the cells in this function by the specified constant. When
	 * call this method, be aware and note that the dividend is non-zero.
	 * </p>
	 * 
	 * @param constant
	 *            constant by which the cells are to be scaled down.
	 */
	public void divide(double constant) {
		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			_cells[i] /= constant;
		}
	}

	/**
	 * <p>
	 * Scales up the cells in this function by the specified constant.
	 * </p>
	 * 
	 * @param constant
	 *            constant by which the cells are to be scaled up.
	 */
	public Function times(double constant) {
		Function f = clone();

		int domainSize = getDomainSize();
		for (int i = 0; i < domainSize; i++) {
			f._cells[i] *= constant;
		}

		return f;
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
	public void multiply(Function function) {
		int fDim = getDimension();
		int gDim = function.getDimension();

		if (fDim == 0 || gDim == 0) {
			multiply(function._cells[0]);
			return;
		}

		int[] gMap = new int[gDim];
		int fIndex = 0;
		int gIndex = 0;
		while (true) {
			if (function._variables[gIndex] == _variables[fIndex]) {
				gMap[gIndex++] = fIndex++;
				if (gIndex == gDim)
					break;
			} else {
				fIndex++;
			}
		}

		int[] fStates = new int[fDim];
		int[] gStates = new int[gDim];
		for (int i = 0; i < getDomainSize(); i++) {
			// one-dimensional index to multi-dimensional indices
			computeStates(i, fStates);

			// projects to states in g
			for (int j = 0; j < gDim; j++) {
				gStates[j] = fStates[gMap[j]];
			}
			double gcell = function._cells[function.computeIndex(gStates)];
			_cells[i] *= gcell;
		}
		return;
	}

	/**
	 * Judge whether this function contains zero cell. This judgement is used
	 * for deciden whether the function can be a dividend.
	 * 
	 * @return
	 */
	public boolean hasZeroCell() {
		boolean has = false;
		for (int i = 0; i < getDomainSize(); i++) {
			if (_cells[i] == 0.0) {
				has = true;
				break;
			}
		}
		return has;
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
	public boolean superiorTo(Function function) {
		boolean superior = true;

		int i = 0;
		int length = function._variables.length;
		while (i < length) {
			if (!contains(function._variables[i])) {
				superior = false;
				break;
			}
			i++;
		}

		return superior;
	}

	/**
	 * <p>
	 * Divide this function by the argument function. Note that this function
	 * must contains the argument function in terms of the variables. When use
	 * this method, make sure that the argument function should NOT contain zero
	 * cell at all.
	 * </p>
	 * 
	 * @param function
	 * @return
	 */
	public void divide(Function function) {
		int fDim = getDimension();
		int gDim = function.getDimension();

		if (fDim == 0 || gDim == 0) {
			divide(function._cells[0]);
			return;
		}

		int[] gMap = new int[gDim];
		int fIndex = 0;
		int gIndex = 0;
		while (true) {
			if (function._variables[gIndex] == _variables[fIndex]) {
				gMap[gIndex++] = fIndex++;
				if (gIndex == gDim)
					break;
			} else {
				fIndex++;
			}
		}

		int[] fStates = new int[fDim];
		int[] gStates = new int[gDim];
		for (int i = 0; i < getDomainSize(); i++) {
			// one-dimensional index to multi-dimensional indices
			computeStates(i, fStates);

			// projects to states in g
			for (int j = 0; j < gDim; j++) {
				gStates[j] = fStates[gMap[j]];
			}
			double gcell = function._cells[function.computeIndex(gStates)];
			_cells[i] /= gcell;
		}
		return;
	}

	/**
	 * <p>
	 * Returns the product between this function and the specified function.
	 * </p>
	 * 
	 * <p>
	 * This method is time critical. It is intensively used in inference 2 *
	 * algorithms.
	 * </p>
	 * 
	 * @param function
	 *            multiplier function.
	 * @return the product between this function and the specified function.
	 */
	public Function times(Function function) {
		int fDim = getDimension();
		int gDim = function.getDimension();

		if (fDim == 0) {
			return function.times(_cells[0]);
		} else if (gDim == 0) {
			return times(function._cells[0]);
		}

		// union of variables and maps from current variables to the union
		ArrayList<Variable> variables = new ArrayList<Variable>(fDim + gDim);
		int[] fMap = new int[fDim];
		int[] gMap = new int[gDim];

		// computes union of variables and enforces the order
		int i = 0, j = 0, k = 0;
		while (i < fDim && j < gDim) {
			int compare = _variables[i].compareTo(function._variables[j]);

			if (compare < 0) {
				variables.add(_variables[i]);
				fMap[i++] = k++;
			} else if (compare > 0) {
				variables.add(function._variables[j]);
				gMap[j++] = k++;
			} else {
				variables.add(_variables[i]);
				fMap[i++] = k;
				gMap[j++] = k++;
			}
		}

		// at most one of two for loops below will essentially execute
		for (; i < fDim; i++) {
			variables.add(_variables[i]);
			fMap[i] = k++;
		}

		for (; j < gDim; j++) {
			variables.add(function._variables[j]);
			gMap[j] = k++;
		}

		// product function: could be Function1D Function2D or just Function
		Function h = createFunction(variables.toArray(new Variable[variables
				.size()]));

		int hDim = h.getDimension();
		int hDomainSize = h.getDomainSize();

		// fills in cells in h
		int[] fStates = new int[fDim];
		int[] gStates = new int[gDim];
		int[] hStates = new int[hDim];

		for (i = 0; i < hDomainSize; i++) {
			// one-dimensional index to multi-dimensional indices
			h.computeStates(i, hStates);

			// projects to states in f
			for (j = 0; j < fDim; j++) {
				fStates[j] = hStates[fMap[j]];
			}

			// projects to states in g
			for (j = 0; j < gDim; j++) {
				gStates[j] = hStates[gMap[j]];
			}

			// multi-dimensional indices to one-dimensional index
			// h._cells[i] = _cells[computeIndex(fStates)]
			// * function._cells[function.computeIndex(gStates)];

			// anything multiply by zero is zero, avoiding zero times
			// infinity resulting in NaN
			double fcell = _cells[computeIndex(fStates)];
			double gcell = function._cells[function.computeIndex(gStates)];
			h._cells[i] = (fcell == 0 || gcell == 0) ? 0 : fcell * gcell;
		}
		return h;
	}

	/**
	 * Returns a string representation of this function. This implementation
	 * returns <code>toString(0)</code>.
	 * 
	 * @return a string representation of this function.
	 * @see #toString(int)
	 */
	public String toString() {
		return toString(0);
	}

	/**
	 * Returns a string representation of this function. The string
	 * representation will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this function.
	 */
	public String toString(int amount) {
		// amount must be non-negative
		assert amount >= 0;

		int dimension = getDimension();
		int domainSize = getDomainSize();

		// prepares white space for indent
		StringBuffer whiteSpace = new StringBuffer();
		for (int i = 0; i < amount; i++) {
			whiteSpace.append('\t');
		}

		// builds string representation
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(whiteSpace);
		stringBuffer.append("function {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tdimension = " + dimension + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tvariables = { ");

		for (Variable variable : _variables) {
			stringBuffer.append("\"" + variable.getName() + "\" ");
		}
		stringBuffer.append("};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tdomain size = " + domainSize + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tcells = [\n");

		int[] states = new int[dimension];
		for (int i = 0; i < domainSize; i++) {
			stringBuffer.append(whiteSpace);
			stringBuffer.append("\t\tf( ");

			// one-dimensional index to multi-dimensional indices
			computeStates(i, states);

			for (int j = 0; j < dimension; j++) {
				stringBuffer.append("\""
						+ _variables[j].getStates().get(states[j]) + "\" ");
			}

			stringBuffer.append(") = " + (float) _cells[i] + "\n");
		}

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\t];\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

	// TODO: LP - See whether it should be included
	@Deprecated
	public double getValue(int[] states) {
		return _cells[computeIndex(states)];
	}

	/**
	 * Returns the list of variables in this function. The returned list is
	 * unmodifiable.
	 * 
	 * @return the list of variables in this function
	 */
	public List<Variable> getVariables() {
		return Collections.unmodifiableList(Arrays.asList(_variables));
	}

	public void reorderStates(Variable variable, int[] stateOrder) {
		List<Variable> variables = getVariables();
		FunctionIterator iterator = new FunctionIterator(this, variables);
		iterator.iterate(new StateReorderingVisitor(
				variables.indexOf(variable), stateOrder));
	}

	private class StateReorderingVisitor implements FunctionIterator.Visitor {
		public StateReorderingVisitor(int variableIndex, int[] stateOrder) {
			this.variableIndex = variableIndex;
			this.stateOrder = stateOrder;
		}

		public void visit(List<Variable> order, int[] states, double value) {
			// do only once when iterating along the target variable
			if (states[variableIndex] != 0)
				return;

			// clone the states so that it won't affect the calling function
			states = states.clone();

			double[] originalValues = new double[_variables[variableIndex]
					.getCardinality()];
			for (int i = 0; i < originalValues.length; i++) {
				states[variableIndex] = i;
				originalValues[i] = _cells[computeIndex(states)];
			}

			for (int i = 0; i < originalValues.length; i++) {
				states[variableIndex] = i;
				_cells[computeIndex(states)] = originalValues[stateOrder[i]];
			}
		}

		private final int variableIndex;
		private final int[] stateOrder;
	}
}