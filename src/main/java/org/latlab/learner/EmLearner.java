/**
 * EmLearner.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.learner;

import java.util.HashMap;
import java.util.HashSet;

import org.latlab.graph.AbstractNode;
import org.latlab.model.*;
import org.latlab.reasoner.*;
import org.latlab.util.*;
import org.latlab.util.DataSet.DataCase;

/**
 * This class provides an implementation for the Expectation-Maximization (EM)
 * algorithm for BNs. Chickering and Heckerman's restarting strategy will be
 * adopted to avoid local maxima. You need to create an instance of
 * <code>EmLearner</code> and tune the settings. You can then use this instance
 * to train different BNs with different data sets with the same setting.
 * 
 * @author Yi Wang
 * 
 */
public class EmLearner {

	/**
	 * the number of elapsed steps.
	 */
	protected int _nSteps;

	/**
	 * the number of restarts.
	 */
	protected int _nRestarts = 64;

	/**
	 * the threshold to control EM convergence.
	 */
	protected double _threshold = 1e-4;

	/**
	 * the maximum number of steps to control EM convergence.
	 */
	protected int _nMaxSteps = 500;

	/**
	 * So far there are two options: "ChickeringHeckerman" and
	 * "MultipleRestarts"
	 */
	protected String _localMaximaEscapeMethod = "ChickeringHeckerman";

	/**
	 * For "MultipleRestarts" method, the number of preSteps to go in order to
	 * choose a good starting point.
	 */
	protected int _nPreSteps = 10;

	/**
	 * When using the Chickering-Heckerman mathod to choose a good starting
	 * point, we first generate _nRestarts random restarts. Then before
	 * eliminaing some bad restarts, we run numInitIterations emStep() for all
	 * random restarts.
	 */
	protected int _numInitIterations = 1;

	/**
	 * the flag indicates whether we reuse the parameters of the input BN as a
	 * candidate starting point.
	 */
	protected boolean _reuse = true;
	
	private HashSet<String> _dontUpdateNodes = null;

	/**
	 * Selects a good starting point using Chickering and Heckerman's strategy.
	 * Note that this restarting phase will terminate midway if the maximum
	 * number of steps is reached. However, it will not terminate if the EM
	 * algorithm already converges on some starting point. That makes things
	 * complicated.
	 * 
	 * @param bayesNet
	 *            input BN.
	 * @param dataSet
	 *            data set to be used.
	 * @return the CTP for the best starting point.
	 */
	private CliqueTreePropagation chickeringHeckermanRestart(BayesNet bayesNet,
			DataSet dataSet) {
		// generates random starting points and CTPs for them
		CliqueTreePropagation[] ctps = new CliqueTreePropagation[_nRestarts];
		double[] lastStepCtps = new double[_nRestarts];

		for (int i = 0; i < _nRestarts; i++) {
			BayesNet copy = bayesNet.clone();

			// in case we reuse the parameters of the input BN as a starting
			// point, we put it at the first place.
			if (!_reuse || i != 0) 
			{	
				if(_dontUpdateNodes == null)
				{
					copy.randomlyParameterize();
				}else
				{
					for(AbstractNode node : copy.getNodes())
					{
						if(!_dontUpdateNodes.contains(node.getName()))
						{
							Function cpt = ((BeliefNode)node).getCpt();
							cpt.randomlyDistribute(((BeliefNode)node).getVariable());
							((BeliefNode)node).setCpt(cpt);
						}
					}
				}
			}

			if (copy instanceof LTM) {
				ctps[i] = new CliqueTreePropagation((LTM) copy);
			} else {
				ctps[i] = new CliqueTreePropagation(copy);
			}
		}

		// We run several steps of emStep before killing starting points for two
		// reasons: 1. the loglikelihood computed is always that of previous
		// model. 2. When reuse, the reused model is kind of dominant because
		// maybe it has alreay EMed.
		for (int j = 0; j < _numInitIterations; j++) {
			for (int i = 0; i < _nRestarts; i++) {
				emStep(ctps[i], dataSet);				
			}
			_nSteps++;
		}

		// game starts, half ppl die in each round :-)
		int nCandidates = _nRestarts;
		int nStepsPerRound = 1;
		
		while (nCandidates > 1 && _nSteps < _nMaxSteps) 
		{	
			// runs EM on all starting points for several steps
			for (int j = 0; j < nStepsPerRound; j++) 
			{	
				boolean noImprovements = true;	
				for (int i = 0; i < nCandidates; i++) 
				{	
					lastStepCtps[i]=ctps[i].getBayesNet().getBICScore(dataSet);
					emStep(ctps[i], dataSet);
						
//					System.out.println("BIC: "+ctps[i].getBayesNet().getBICScore(dataSet));
//					System.out.println("Last: "+lastStepCtps[i]);
					
					if(ctps[i].getBayesNet().getBICScore(dataSet)-lastStepCtps[i]>_threshold || lastStepCtps[i] == Double.NEGATIVE_INFINITY)
					{
						noImprovements = false;
					}
				}			
				_nSteps++;
				
				if(noImprovements)
				{
					return ctps[0];
				}
			}

			// sorts BNs in descending order with respect to loglikelihoods
			for (int i = 0; i < nCandidates - 1; i++) {
				for (int j = i + 1; j < nCandidates; j++) {
					if (ctps[i].getBayesNet().getLoglikelihood(dataSet) < ctps[j]
							.getBayesNet().getLoglikelihood(dataSet)) {
						CliqueTreePropagation tempCtp = ctps[i];
						ctps[i] = ctps[j];
						ctps[j] = tempCtp;
						
					}
				}
			}

			// retains top half
			nCandidates /= 2;

			// doubles EM steps subject to maximum step constraint
			nStepsPerRound = Math.min(nStepsPerRound * 2, _nMaxSteps - _nSteps);			
		}

		// returns the CTP for the best starting point
		return ctps[0];
	}

	/**
	 * Returns an optimized BN with respect to the specified data set. Note that
	 * the argument BN will not change.
	 * 
	 * @param bayesNet
	 *            BN to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 * @return an optimized BN.
	 */
	public BayesNet em(BayesNet bayesNet, DataSet dataSet) {

//		System.out.println("Begain full EM: ");
//
//		long start = System.currentTimeMillis();
		// resets the number of EM steps
		_nSteps = 0;

		// selects a good starting point
		CliqueTreePropagation ctp = chickeringHeckermanRestart(bayesNet, dataSet);

		emStep(ctp, dataSet);
		_nSteps++;

		// runs EM steps until convergence
		double loglikelihood;
		bayesNet = ctp.getBayesNet();
		do {
			loglikelihood = bayesNet.getLoglikelihood(dataSet);
			emStep(ctp, dataSet);
			_nSteps++;
		} while (bayesNet.getLoglikelihood(dataSet) - loglikelihood > _threshold
				&& _nSteps < _nMaxSteps);

//		System.out.println("=== Elapsed Time: "
//				+ (System.currentTimeMillis() - start) + " ms ===, and steps"
//				+ _nSteps);

		return bayesNet;
	}

	/**
	 * Runs one EM step on the specified BN using the specified CTP as the
	 * inference algorithm and returns the loglikelihood of the BN associated
	 * with the input CTP.
	 * 
	 * @param ctp
	 *            CTP for the BN to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 * @return the loglikelihood of the BN associated with the input CTP.
	 */
	// private final void emStep(CliqueTreePropagation ctp, DataSet dataSet) {
	public final void emStep(CliqueTreePropagation ctp, DataSet dataSet) {
		// gets the BN to be optimized
		BayesNet bayesNet = ctp.getBayesNet();

		// sufficient statistics for each node
		HashMap<Variable, Function> suffStats = new HashMap<Variable, Function>();

		double loglikelihood = 0.0;
//		double minLoglikelihood = Double.POSITIVE_INFINITY;
//		int numZero = 0;
		// computes datum by datum
		for (DataCase dataCase : dataSet.getData()) {
			double weight = dataCase.getWeight();

			// sets evidences
			ctp.setEvidence(dataSet.getVariables(), dataCase.getStates());

			// propagates
			double likelihoodDataCase = ctp.propagate();

			// updates sufficient statistics for each node
			for (Variable var : bayesNet.getVariables()) {
				
				if(_dontUpdateNodes != null && _dontUpdateNodes.contains(var.getName()))
					continue;
				
				Function fracWeight = ctp.computeFamilyBelief(var);

				fracWeight.multiply(weight);

				if (suffStats.containsKey(var)) {
					suffStats.get(var).plus(fracWeight);
				} else {
					suffStats.put(var, fracWeight);
				}
			}
			
			loglikelihood += Math.log(likelihoodDataCase) * weight;
			
		}

		// updates parameters
		for (AbstractNode node : bayesNet.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;
			
			if(_dontUpdateNodes != null && _dontUpdateNodes.contains(bNode.getName()))
				continue;
			
			Function cpt = suffStats.get(bNode.getVariable());
			cpt.normalize(bNode.getVariable());
			bNode.setCpt(cpt);
		}
		
		//In case that likelihoodDataCase == 0, replace it with the smallest non-zero value.
		//Inspired from Choi's code. This is very unlikely to happen.
//		loglikelihood += numZero*Math.log(minLoglikelihood);
//		System.out.println("prob( record" + " ) = 0.0" + " weight: " + numZero);
		
		// updates loglikelihood of optimized BN
		bayesNet.setLoglikelihood(dataSet, loglikelihood);
		
		//System.out.println("step:"+_nSteps+", BIC:"+ctp.getBayesNet().getBICScore(dataSet));
	}

	/**
	 * Returns the maximum number of steps allowed in this EM algorithm.
	 * 
	 * @return the maximum number of steps.
	 */
	public final int getMaxNumberOfSteps() {
		return _nMaxSteps;
	}

	/**
	 * Returns the number of restarts of this EM algorithm.
	 * 
	 * @return the number of restarts.
	 */
	public final int getNumberOfRestarts() {
		return _nRestarts;
	}

	/**
	 * Returns <code>true</code> if we will reuse the parameters of the input BN
	 * as a starting point.
	 * 
	 * @return <code>true</code> if we will reuse the parameters of the input BN
	 *         as a starting point.
	 */
	public final boolean getReuseFlag() {
		return _reuse;
	}

	/**
	 * Returns the number of elapsed steps in last EM run.
	 * 
	 * @return the number of elapsed steps in last EM run.
	 */
	public final int getNumberOfSteps() {
		return _nSteps;
	}

	/**
	 * Returns the threshold of this EM algorithm.
	 * 
	 * @return the threshold.
	 */
	public final double getThreshold() {
		return _threshold;
	}

	/**
	 * Returns the method used to avoid local maxima.
	 * 
	 * @return localMaximaEscapeMethod = "ChickeringHeckerman" or
	 *         "MultipleRestarts"
	 */
	public String getLocalMaximaEscapeMethod() {
		return _localMaximaEscapeMethod;
	}

	/**
	 * Reutrns the number of preSteps when using "MultipleRestarts" method.
	 * 
	 * @return
	 */
	public int getNumberOfPreSteps() {
		return _nPreSteps;
	}

	/**
	 * Returns the method used to avoid local maxima.
	 * 
	 * @return localMaximaEscapeMethod = "ChickeringHeckerman" or
	 *         "MultipleRestarts"
	 */
	public void setLocalMaximaEscapeMethod(String methodOption) {

		assert methodOption.equals("ChickeringHeckerman")
				|| methodOption.equals("MultipleRestarts");

		_localMaximaEscapeMethod = methodOption;
	}

	/**
	 * Set the number of preSteps when using "MultipleRestarts" method.
	 * 
	 * @return
	 */
	public void setNumberOfPreSteps(int nPreSteps) {
		// the number of steps must be positive
		assert nPreSteps > 0;

		_nPreSteps = nPreSteps;
	}

	/**
	 * Replaces the maximum number of steps allowed in this EM algorithm.
	 * 
	 * @param nMaxSteps
	 *            new maximum number of steps.
	 */
	public final void setMaxNumberOfSteps(int nMaxSteps) {
		// maximum number of steps must be positive
		assert nMaxSteps > 0;

		_nMaxSteps = nMaxSteps;
	}

	/**
	 * Replaces the number of restarts of this EM algorithm.
	 * 
	 * @param nRestarts
	 *            new number of restarts.
	 */
	public final void setNumberOfRestarts(int nRestarts) {
		// number of restarts must be positive
		assert nRestarts > 0;

		_nRestarts = nRestarts;
	}

	/**
	 * Replaces the flag that indicates whether we will reuse the parameters of
	 * the input BN as a starting point.
	 * 
	 * @param reuse
	 *            new flag.
	 */
	public final void setReuseFlag(boolean reuse) {
		_reuse = reuse;
	}

	/**
	 * Replaces the threshold of this EM algorithm.
	 * 
	 * @param threshold
	 *            new threshold.
	 */
	public final void setThreshold(double threshold) {
		// threshold must be non-negative
		assert threshold >= 0.0;

		_threshold = threshold;
	}

	/**
	 * Reset the number of initial iterations of emStep().
	 * 
	 * @param threshold
	 *            new threshold.
	 */
	public final void setNumInitIterations(int numInitIterations) {
		assert numInitIterations >= 0;
		_numInitIterations = numInitIterations;
	}
	
	public void setDontUpdateNodes(HashSet<String> DontUpdate)
	{
		_dontUpdateNodes = DontUpdate;
	}

}