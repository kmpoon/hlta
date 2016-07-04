//package org.latlab.util;
//
//import org.latlab.util.DataSet.DataCase;
//import org.latlab.model.*;
//import org.latlab.reasoner.*;
//
//public class ScoreCalculator {
//
//	public static double computeLoglikelihood(LTM model, DataSet dataSet) {
//		double loglikelihood = 0.0;
//		CliqueTreePropagation ctp = new CliqueTreePropagation(model);
//		// computes datum by datum
//		for (DataCase dataCase : dataSet.getData()) {
//			double weight = dataCase.getWeight();
//			// sets evidences
//			ctp.setEvidence(dataSet.getVariables(), dataCase.getStates());
//			// propagates
//			double likelihoodDataCase = ctp.propagate();
//			if (likelihoodDataCase == 0.0)
//				System.out
//						.println("ScoreCalculator.computeLoglihelihood:  prob( record"
//								+ " ) = 0.0" + " weight: " + weight);
//			else
//				// updates loglikelihood
//				loglikelihood += Math.log(likelihoodDataCase) * weight;
//		}
//		model.setLoglikelihood(dataSet, loglikelihood);
//		return loglikelihood;
//	}
//
//}

package org.latlab.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.model.*;
import org.latlab.reasoner.*;
import org.latlab.util.DataSet.DataCase;

public class ScoreCalculator {

	/**
	 * All available score types.
	 */
	private final static String[] SCORES = { "AIC", "BIC", "Logscore" };

	/**
	 * Returns the AIC score of the given model w.r.t. the specified data.
	 * 
	 * @param model
	 *            The model to be evaluated.
	 * @param data
	 *            The data.
	 * @return The AIC score of the given model w.r.t. the specified data.
	 */
	public static double computeAic(BayesNet model, DataSet data) {
		return computeLoglikelihood(model, data) - model.computeDimension();
	}

	/**
	 * Returns the BIC score of the given model w.r.t. the specified data.
	 * 
	 * @param model
	 *            The model to be evaluated.
	 * @param data
	 *            The data.
	 * @return The BIC score of the given model w.r.t. the specified data.
	 */
	public static double computeBic(BayesNet model, DataSet data) {
		return computeLoglikelihood(model, data) - model.computeDimension()
				* Math.log(data.getTotalWeight()) / 2.0;
	}

	/**
	 * Returns the loglikelihood of the given model w.r.t. the specified data.
	 * 
	 * @param model
	 *            The model to be evaluated.
	 * @param data
	 *            The data.
	 * @return The loglikelihood of the given model w.r.t. the specified data.
	 */
	public static double computeLoglikelihood(BayesNet model, DataSet data) {
		// synchronize first
		data = data.synchronize(model);

		double loglikelihood = 0.0;
		double loglikelihoodAlternative = 0;

		CliqueTreePropagation ctp = null;
		if (model instanceof LTM) {
			ctp = new CliqueTreePropagation((LTM) model);
		} else {
			ctp = new CliqueTreePropagation(model);
		}

		// computes datum by datum
		int i = 1;
		for (DataCase dataCase : data.getData()) {
			double weight = dataCase.getWeight();
			// sets evidences
			ctp.setEvidence(data.getVariables(), dataCase.getStates());
			// propagates
			double likelihoodDataCase = ctp.propagate();
			double loglikelihoodDataCaseAlternative =
					ctp.getLastLogLikelihood();
			if (likelihoodDataCase < Double.MIN_NORMAL) {
				System.out.printf(
						"ScoreCalculator.computeLoglihelihood %d-th case:  "
								+ "prob( record ) = %e weight: %f, "
								+ "alternativeLogLikelihood: %f\n", i,
						likelihoodDataCase, weight,
						loglikelihoodDataCaseAlternative);
			}

			if (likelihoodDataCase != 0.0) {
				// updates loglikelihood
				loglikelihood += Math.log(likelihoodDataCase) * weight;
			}

			loglikelihoodAlternative +=
					loglikelihoodDataCaseAlternative * weight;

			i++;
		}

		if (Math.abs(loglikelihood - loglikelihoodAlternative) > 1e-6) {
			System.out.printf(
					"In ScoreCalculator.computeLoglikelihood, "
							+ "loglikelihood (%e) and loglikelihoodAlternative (%e) "
							+ "do not match. It uses loglikelihoodAlternative (%f) now.\n",
					loglikelihood, loglikelihoodAlternative,
					loglikelihoodAlternative);
		}

		model.setLoglikelihood(data, loglikelihoodAlternative);
		return loglikelihoodAlternative;
	}

	/**
	 * Returns the conditional loglikelihood of the given model w.r.t. the
	 * specified data.
	 * 
	 * @param model
	 *            The model to be evaluated.
	 * @param data
	 *            The data.
	 * @param condVar
	 *            The conditioning variable.
	 * @return The conditional loglikelihood of the given model w.r.t. the
	 *         specified data.
	 */
	public static double computeCll(BayesNet model, DataSet data,
			Variable condVar) {
		// synchronize first
		data = data.synchronize(model);
		int index = Arrays.binarySearch(data.getVariables(), condVar);
		int[] states = new int[data.getDimension()];
		BeliefNode node = model.getNode(condVar);

		double loglikelihood = 0.0;
		CliqueTreePropagation ctp = new CliqueTreePropagation(model);
		// computes datum by datum
		for (DataCase dataCase : data.getData()) {
			double weight = dataCase.getWeight();
			// sets evidences
			System.arraycopy(dataCase.getStates(), 0, states, 0,
					data.getDimension());
			int condState = states[index];
			states[index] = -1;
			ctp.setEvidence(data.getVariables(), states);
			// propagates
			double likelihoodDataCase = ctp.propagate();
			if (likelihoodDataCase == 0.0) {
				System.out.printf("ScoreCalculator.computeLoglihelihood:  "
						+ "prob( record ) = %e weight: %f\n",
						likelihoodDataCase, weight);
			} else
				// updates loglikelihood
				loglikelihood +=
						Math.log(ctp.computeBelief(node.getVariable()).getCells()[condState])
								* weight;
		}
		model.setLoglikelihood(data, loglikelihood);
		return loglikelihood;
	}

	/*
	 * This serves as a reposiroty of messages.
	 */
	private Map<DataCase, CliqueTreePropagation> _ctps =
			new HashMap<DataCase, CliqueTreePropagation>();

	// public static double computeLoglikelihood(LTM model, DataSet dataSet) {
	// double loglikelihood = 0.0;
	// CliqueTreePropagation ctp = new CliqueTreePropagation(model);
	// // computes datum by datum
	// for (DataCase dataCase : dataSet.getData()) {
	// double weight = dataCase.getWeight();
	// // sets evidences
	// ctp.setEvidence(dataSet.getVariables(), dataCase.getStates());
	// // propagates
	// double likelihoodDataCase = ctp.propagate();
	// // if (likelihoodDataCase == 0.0)
	// // System.out
	// // .println("ScoreCalculator.computeLoglihelihood:  prob( record"
	// // + " ) = 0.0" + " weight: " + weight);
	// // else
	// // updates loglikelihood
	// loglikelihood += Math.log(likelihoodDataCase) * weight;
	// }
	// model.setLoglikelihood(dataSet, loglikelihood);
	// return loglikelihood;
	// }

	/**
	 * Returns the score of the given model w.r.t. the specified data.
	 * 
	 * @param model
	 *            The model to be evaluated.
	 * @param data
	 *            The data.
	 * @param score
	 *            The score type.
	 * @return The score of the given model w.r.t. the specified data.
	 */
	public static double computeScore(BayesNet model, DataSet data, String score) {
		if (score.equals("BIC")) {
			return computeBic(model, data);
		} else if (score.equals("AIC")) {
			return computeAic(model, data);
		} else if (score.equals("Logscore")) {
			return computeLoglikelihood(model, data);
		} else {
			return Double.NEGATIVE_INFINITY;
		}
	}

	/**
	 * Returns all available scores.
	 * 
	 * @return All available scores.
	 */
	public static String[] getAvailableScores() {
		return SCORES;
	}

	/**
	 * Given LTM model (m, theta) and an incomplete dataset D. The method (1)
	 * complete the dataset using the model to obtain a cimpleted data set
	 * D_comp, and (2)reestmate the model's MLE w.r.t D_comp. Suppose the
	 * learned model is (m theta*). The final step is to compute the
	 * loglikelihood logP(D_comp|m, theta*).
	 * 
	 * Logically there are three steps. In practice, the last two step can be
	 * combined in that it is not necessary to explicitly represent the theta*.
	 * 
	 * @param model
	 * @param dataset
	 * @return
	 */
	public double computeMaximumLoglikelihood_CompletedData(LTM model,
			DataSet dataset) {
		// Compute the messages needed and store them in the _ctps
		computeCtps(model, dataset);

		double score = 0.0;

		for (AbstractNode Anode : model.getNodes()) {
			BeliefNode Bnode = (BeliefNode) Anode;
			Variable vi = Bnode.getVariable();
			if (Bnode.isRoot()) {
				Function ss = computeSuffStats(dataset, vi, vi);
				Function condProb = ss.clone();
				condProb.normalize(vi);

				score += localLoglikelihood(ss, condProb);
			} else {
				BeliefNode parent = (BeliefNode) Bnode.getParent();
				Variable vj = parent.getVariable();
				// ss = N(vi, vj); Note that P(vi|vj) is the parameter
				Function ss = computeSuffStats(dataset, vi, vj);
				// The next two lines is to compute condProb in the MLE
				// configuration.
				Function condProb = ss.clone();
				condProb.normalize(vi);

				score += localLoglikelihood(ss, condProb);
			}
		}

		return score;
	}

	/**
	 * For each datacase, compute the corresponding ct after propogation. Note
	 * that the clique tree is constructed for HLCM rather than BayesNet.
	 * 
	 * @param model
	 *            The HLCM
	 * @param data
	 *            A collection of DataCases
	 * @return
	 */
	private void computeCtps(LTM model, DataSet data) {
		// System.out.println("Begin computing ctps for current model");
		_ctps.clear();
		CliqueTreePropagation ctp = new CliqueTreePropagation(model);
		for (DataCase dataCase : data.getData()) {
			CliqueTreePropagation copy = ctp.clone();
			copy.setEvidence(data.getVariables(), dataCase.getStates());
			copy.propagate();
			// clean up the clique tree
			for (AbstractNode node : copy.getCliqueTree().getNodes()) {
				CliqueNode cNode = (CliqueNode) node;
				// cNode.clearFunctions();
				cNode.clearQualifiedNeiMsgs();
				cNode.setMsgsProd(null);
			}
			_ctps.put(dataCase, copy);
		}
		// System.out.println("End computing ctps for current model");
	}

	/**
	 * We compute the posterior sufficient statistics for the corresponding two
	 * variables based on the already-computed ctps.
	 * 
	 * Note: Sometimes the var1 or var2 maybe hidden or maybe observed. It
	 * dependens on the dataCase. However, the return should always be a
	 * function of (var1, var2). Note that var1 and var2 can be the same
	 * variable.
	 * 
	 * @param data
	 * @param var1
	 * @param var2
	 * @return
	 */
	private Function computeSuffStats(DataSet data, Variable var1, Variable var2) {

		LTM model = (LTM) _ctps.values().iterator().next().getBayesNet();
		ArrayList<BeliefNode> bNodesOnPath = model.computePath(var1, var2);

		Set<Variable> queryVars = new HashSet<Variable>();
		queryVars.add(var1);
		queryVars.add(var2);

		Function suffStats = null;
		for (DataCase dataCase : data.getData()) {
			CliqueTreePropagation ctp = _ctps.get(dataCase);
			CliqueTree ct = ctp.getCliqueTree();

			Set<CliqueNode> CliqueSubTree = new HashSet<CliqueNode>();
			for (BeliefNode bNode : bNodesOnPath) {
				CliqueNode variable = ct.getVariableClique(bNode.getVariable());
				CliqueNode family = ct.getFamilyClique(bNode.getVariable());
				if (variable != null) {
					CliqueSubTree.add(variable);
				}
				CliqueSubTree.add(family);
			}

			Function fracWeight = ctp.computeBelief(queryVars, CliqueSubTree);

			fracWeight.multiply(dataCase.getWeight());

			if (suffStats == null)
				suffStats = fracWeight;
			else
				suffStats.plus(fracWeight);
		}
		return suffStats;
	}

	/**
	 * Note that the condProb is changed during the computation. 1. This method
	 * calculates \sum_{X, Y} N(X, Y) log P(X|Y), where N(X, Y) is a function of
	 * sufficient statistics. 2. Compute \sum_(root) N(root) log P(root) where
	 * N(root) is a function of sufficient statistics.
	 */
	private double localLoglikelihood(Function suffStats, Function condProb) {
		condProb.log();
		condProb.multiply(suffStats);
		return condProb.sumUp();
	}
}
