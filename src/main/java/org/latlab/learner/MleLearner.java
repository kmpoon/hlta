/**
 * MleLearner.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.learner;

import java.util.ArrayList;
import java.util.Arrays;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Variable;

/**
 * This class provides an implementation for maximum likelihood estimates (MLEs)
 * for parameters in BNs with complete data. No need to tune any configurations
 * at all. Simply call the static method <code>MleLearner.computeMle</code>
 * for MLEs.
 * 
 * @author Yi Wang
 * 
 */
public final class MleLearner {

	/**
	 * Optimizes parameters in the specified BN with respect to the specified
	 * data set. The argument BN will be modified directly. Keep a copy by
	 * yourself if necessary.
	 * 
	 * @param bayesNet
	 *            BN whose parameters are to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 */
	public final static void computeMle(BayesNet bayesNet, DataSet dataSet) {
		for (AbstractNode node : bayesNet.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;

			// retrieve variables in family
			ArrayList<Variable> family = new ArrayList<Variable>();
			family.add(bNode.getVariable());

			for (DirectedNode parent : bNode.getParents()) {
				family.add(((BeliefNode) parent).getVariable());
			}

			// derives CPT from sufficient statistics
			Function cpt = Function.createFunction(dataSet.project(family));
			cpt.normalize(bNode.getVariable());

			// sets CPT
			bNode.setCpt(cpt);
		}
	}

	/**
	 * Optimizes parameters in the specified BN with respect to the specified
	 * data set. A uniform prior can be incorporated by setting the pseudo
	 * count. The argument BN will be modified directly. Keep a copy by yourself
	 * if necessary.
	 * 
	 * @param bayesNet
	 *            BN whose parameters are to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 * @param alpha
	 *            smoothing factor to incorporate.
	 */
	public final static void computeMle(BayesNet bayesNet, DataSet dataSet,
			double alpha) {
		for (AbstractNode node : bayesNet.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;

			// retrieve variables in family
			ArrayList<Variable> family = new ArrayList<Variable>();
			family.add(bNode.getVariable());

			for (DirectedNode parent : bNode.getParents()) {
				family.add(((BeliefNode) parent).getVariable());
			}

			// derives CPT from sufficient statistics
			Function cpt = Function.createFunction(dataSet.project(family));

			// incorporate prior
			double[] cells = cpt.getCells();
			for (int i = 0; i < cells.length; i++) {
				cells[i] += alpha;
			}

			cpt.normalize(bNode.getVariable());

			// sets CPT
			bNode.setCpt(cpt);
		}
	}

	/**
	 * Optimizes parameters in the specified BN with respect to the specified
	 * data set. A marginal prior can be incorporated by setting the pseudo
	 * count. The argument BN will be modified directly. Keep a copy by yourself
	 * if necessary.
	 * 
	 * @param bayesNet
	 *            BN whose parameters are to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 * @param pseudoCount
	 *            marginal prior to incorporate.
	 */
	public final static void computeMleMargPrior(BayesNet bayesNet,
			DataSet dataSet, double pseudoCount) {
		for (AbstractNode node : bayesNet.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;

			// retrieve variables in family
			ArrayList<Variable> family = new ArrayList<Variable>();
			family.add(bNode.getVariable());

			for (DirectedNode parent : bNode.getParents()) {
				family.add(((BeliefNode) parent).getVariable());
			}

			// sufficient statistics Nijk
			Function cpt = Function.createFunction(dataSet.project(family));

			// use marginal distribution with the given pseudo count as prior
			ArrayList<Variable> var = new ArrayList<Variable>(1);
			var.add(bNode.getVariable());

			// sufficient statistics Nik
			Function marg = Function.createFunction(dataSet.project(var));

			// scaled by N0 / N
			Function prior = Function.createFunction(family);
			Arrays.fill(prior.getCells(), pseudoCount
					/ dataSet.getTotalWeight());
			prior = prior.times(marg);

			// incorporate prior and normalize:
			// (Nijk + Nik * N0 / N) / (Nij + N0)
			cpt.plus(prior);
			cpt.normalize(bNode.getVariable());

			// sets CPT
			bNode.setCpt(cpt);
		}
	}

}
