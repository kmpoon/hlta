/**
 * Utils.java
 * Copyright (C) 2007 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Utils {

	/**
	 * Returns the conditional mutual information I(X;Y|Z) given the 3D
	 * distribution P(X,Y,Z).
	 * 
	 * @param dist
	 *            Distribution P(X,Y,Z).
	 * @param condVar
	 *            Conditional variable Z.
	 * @return The conditional mutual information I(X;Y|Z).
	 */
	public static double computeConditionalMutualInformation(Function dist,
			Variable condVar) {
		// ensure the distribution contains three variables
		assert dist.getDimension() == 3;

		// ensure the distribution contains the conditional variable
		assert dist.contains(condVar);

		// ensure the distribution sum up to one
		assert dist.sumUp() == 1.0;

		// I(X;Y|Z) = sum_X,Y,Z P(X,Y,Z) log P(X,Y|Z)/P(X|Z)P(Y|Z)
		// = sum_X,Y,Z P(X,Y,Z) log P(X,Y,Z)P(Z)/P(X,Z)P(Y,Z)
		List<Variable> vars = dist.getVariables();
		Variable x = vars.get(0);
		Variable y = vars.get(1);
		if (x == condVar) {
			x = vars.get(2);
		}
		if (y == condVar) {
			y = vars.get(2);
		}

		Function pxz = dist.sumOut(y);
		Function pyz = dist.sumOut(x);
		Function pz = pxz.sumOut(x);

		// cells of joint, numerator, and denominator
		double[] distCells = dist._cells;
		double[] numCells = dist.times(pz)._cells;
		double[] denomCells = pxz.times(pyz)._cells;
		int size = dist.getDomainSize();

		double cmi = 0.0;
		for (int i = 0; i < size; i++) {
			// skip if either denominator or numerator equals to zero
			if (denomCells[i] > 0.0 && numCells[i] > 0.0) {
				cmi += distCells[i] * Math.log(numCells[i] / denomCells[i]);
			}
		}

		return cmi;
	}

	/**
	 * Returns the pairwise mutual information given the 2D distribution.
	 * 
	 * @param dist
	 *            distribution over a pair of variables.
	 * @return the pairwise mutual information given the 2D distribution.
	 */
	public static double computeMutualInformation(Function dist) {
		// ensure the distribution contains a pair of variables
		assert dist.getDimension() == 2;

		// ensure the distribution sum up to one
		assert dist.sumUp() == 1.0;

		// cells of joint and two marginal distributions
		double[] cells = dist._cells;

		double[] cells1 = dist.sumOut(dist.getVariables().get(1))._cells;
		double[] cells2 = dist.sumOut(dist.getVariables().get(0))._cells;

		// I(X;Y) = sum_X,Y P(X,Y) log P(X,Y)/P(X)P(Y)
		double mi = 0.0;
		int index = 0;
		for (double cell1 : cells1) {
			for (double cell2 : cells2) {
				double cell = cells[index++];

				// if P(x, y) = 0, skip this term
				if (cell != 0.0) {
					mi += cell * Math.log(cell / (cell1 * cell2));
				}
			}
		}

		return mi;
	}
	
	/**
	 * Returns the pointwise mutual information given the 2D distribution.
	 * 
	 * @param dist
	 *            distribution over a pair of variables.
	 * @return the pointwise mutual information given the 2D distribution.
	 */
	public static double computePointwiseMutualInformation(Function dist) {
		// ensure the distribution contains a pair of variables
		assert dist.getDimension() == 2;

		// ensure the distribution sum up to one
		assert dist.sumUp() == 1.0;

		// cells of joint and two marginal distributions
		double[] cells = dist._cells;
		double[] cells1 = dist.sumOut(dist.getVariables().get(1))._cells;
		double[] cells2 = dist.sumOut(dist.getVariables().get(0))._cells;

		// PMI(X;Y) = P(X=1,Y=1) log P(X=1,Y=1)/P(X=1)P(Y=1)
		double pmi = Math.log(cells[3]/(cells1[1]*cells2[1]));

		return pmi;
	}

	/**
	 * Returns the entropy of the specified distribution.
	 * 
	 * @param dist
	 *            the distribution whose entropy is to be computed.
	 * @return the entropy of the specified distribution.
	 */
	public static double computeEntropy(Function dist) {
		// ensure the distribution sum up to one
		assert dist.sumUp() == 1.0;

		// H(X) = - sum_X P(X) log P(X)
		double ent = 0.0;
		for (double cell : dist._cells) {
			// if P(x) = 0, skip this term
			if (cell != 0.0) {
				ent -= cell * Math.log(cell);
			}
		}

		return ent;
	}

	/**
	 * Returns the KL divergence D(P||Q) between the distributions P and Q. The
	 * convention we use: (1) 0 * log(0) = 0; (2) x * log(0) = 0 (!!!)
	 * 
	 * @param p
	 *            distribution P.
	 * @param q
	 *            distribution Q.
	 * @return the KL divergence D(P||Q).
	 */
	public static double computeKl(Function p, Function q) {
		// ensure two functions over same domain
		assert Arrays.equals(p._variables, q._variables);

		double kl = 0.0;
		double[] pCells = p.getCells();
		double[] qCells = q.getCells();
		for (int i = 0; i < pCells.length; i++) {
			// skip cells where either P or Q instantiate to 0
			if (pCells[i] != 0.0 && qCells[i] != 0.0) {
				kl += pCells[i] * Math.log(pCells[i] / qCells[i]);
			}
		}

		return kl;
	}
	
	public static <T> List<Map.Entry<T,Double>> sortByAscendingOrder(Map<T, Double> collection)
	{
        List<Map.Entry<T,Double>> list = new ArrayList<Map.Entry<T,Double>>(collection.entrySet());
        
        Collections.sort(list, new Comparator<Map.Entry<T,Double>>(){
        	public int compare(Map.Entry<T,Double> o1, Map.Entry<T,Double> o2)
        	{
        		if(o1.getValue() > o2.getValue())
        		{
        			return 1;
        		}else if(o1.getValue() < o2.getValue()) 
        		{
        			return -1;
        		}else
        		{
        			return 0;
        		}
        }
        });
        
        return list;
	}
	
	public static <T> List<Map.Entry<T,Double>> sortByDescendingOrder(Map<T, Double> collection)
	{
        List<Map.Entry<T,Double>> list = new ArrayList<Map.Entry<T,Double>>(collection.entrySet());
        
        Collections.sort(list, new Comparator<Map.Entry<T,Double>>(){
        	public int compare(Map.Entry<T,Double> o1, Map.Entry<T,Double> o2)
        	{
        		if(o1.getValue() > o2.getValue())
        		{
        			return -1;
        		}else if(o1.getValue() < o2.getValue()) 
        		{
        			return 1;
        		}else
        		{
        			return 0;
        		}
        }
        });
        
        return list;
	}

}
