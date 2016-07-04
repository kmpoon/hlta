/**
 * LocalEmLearner.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.learner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.latlab.model.*;
import org.latlab.reasoner.*;
import org.latlab.util.*;
import org.latlab.util.DataSet.DataCase;

/**
 * This class provides an implementation for the local version of the EM
 * algorithm for BNs. In this version of EM, not all parameters but those for a
 * subset of belief nodes can change. Consequently, in each E-step, we only need
 * to recompute sufficient statistics for those mutable nodes. Moreover, for a
 * given data case, messages from immutable cliques will not change between EM
 * steps. Thus we can reuse them. To achieve both goal, we build a CTP for each
 * data case and deploy a partial propagation on it.
 * 
 * @author Yi Wang
 * 
 */
public final class LocalEmLearner extends EmLearner {

	/**
	 * A repository of messages. In thie implementation, this must be prepared
	 * beforehand.
	 */
	private Map<DataCase, Set<MessagesForLocalEM>> _repository;

	/**
	 * We control termination of localEM by number of continued steps.
	 */
	protected int _nContinuedSteps = 10;

	/**
	 * Specify that in M-step, whose Cpt will be updated.
	 */
	protected Variable[] _mutableVars;

	/**
	 * A template Ctp. The useful information conveyed is the cliquetree,
	 * especially the foucused subtree contained.
	 */
	protected CliqueTreePropagation _templateCtp;

	/**
	 * Selects a good starting point using multiple restarts strategy. The
	 * details is as follows, first from _nRestarts initial models we do
	 * _nPreSteps localEM. Then choose the so far best one to continue until the
	 * maximum number of steps is reached or converged.
	 * 
	 * @param bayesNet
	 *            BN to be optimized.
	 * @param dataSet
	 *            data set to be used.
	 * @return the CTPs for the best starting point.
	 */
	private CliqueTreePropagation multipleRestarts(BayesNet bayesNet,
			DataSet dataSet) {
		CliqueTreePropagation[] ctps = new CliqueTreePropagation[_nRestarts];

		for (int i = 0; i < _nRestarts; i++) {
			BayesNet bayesNetCopy = bayesNet.clone();

			// finds mutable nodes in new BN
			ArrayList<BeliefNode> mutableNodesCopy = new ArrayList<BeliefNode>();
			for (Variable var : _mutableVars) {
				mutableNodesCopy.add(bayesNetCopy.getNode(var));
			}

			// in case we reuse the parameters of the input BN as a starting
			// point, we put it at the first place.
			if (!_reuse || i != 0) {
				bayesNetCopy.randomlyParameterize(mutableNodesCopy);
			}

			ctps[i] = _templateCtp.clone();
			ctps[i].setBayesNet(bayesNetCopy);
		}

		for (int j = 0; j < _numInitIterations; j++) {
			for (int i = 0; i < _nRestarts; i++) {
				localEmStep(ctps[i], dataSet);
			}
			_nSteps++;
		}

		for (int j = 0; j < _nPreSteps; j++) {
			for (int i = 0; i < _nRestarts; i++) {
				localEmStep(ctps[i], dataSet);
			}
			_nSteps++;
		}

		CliqueTreePropagation bCtp = null;
		double bLoglikelihood = -Double.MAX_VALUE;
		for (int i = 0; i < _nRestarts; i++) {
			double loglikelihood = ctps[i].getBayesNet().getLoglikelihood(
					dataSet);
			if (loglikelihood > bLoglikelihood) {
				bCtp = ctps[i];
				bLoglikelihood = loglikelihood;
			}
		}
		// returns the CTPs for the best starting point
		return bCtp;
	}

	/**
	 * Selects a good starting point using Chickering and Heckerman's strategy.
	 * 
	 * @param bayesNet
	 *            input BN.
	 * @param dataSet
	 *            data set to be used.
	 * @return the CTP for the best starting point.
	 */
	private final CliqueTreePropagation chickeringHeckermanRestart(
			BayesNet bayesNet, DataSet dataSet) {
		// generates random starting points and CTPs for them
		CliqueTreePropagation[] ctps = new CliqueTreePropagation[_nRestarts];
		double[] lastStepCtps = new double[_nRestarts];

		for (int i = 0; i < _nRestarts; i++) {
			BayesNet bayesNetCopy = bayesNet.clone();

			// finds mutable nodes in new BN
			ArrayList<BeliefNode> mutableNodesCopy = new ArrayList<BeliefNode>();
			for (Variable var : _mutableVars) {
				mutableNodesCopy.add(bayesNetCopy.getNode(var));
			}

			// in case we reuse the parameters of the input BN as a starting
			// point, we put it at the first place.
			if (!_reuse || i != 0) {
				bayesNetCopy.randomlyParameterize(mutableNodesCopy);
			}

			ctps[i] = _templateCtp.clone();
			ctps[i].setBayesNet(bayesNetCopy);
		}

		for (int j = 0; j < _numInitIterations; j++) {
			for (int i = 0; i < _nRestarts; i++) {
				localEmStep(ctps[i], dataSet);
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
					localEmStep(ctps[i], dataSet);
					
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
//			nStepsPerRound = nStepsPerRound * 2;
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
	@Override
	public BayesNet em(BayesNet bayesNet, DataSet dataSet) {
		// resets the number of EM steps
		_nSteps = 0;

		// selects starting point
		CliqueTreePropagation ctp = null;
		if (_localMaximaEscapeMethod.equals("ChickeringHeckerman")) {
			ctp = chickeringHeckermanRestart(bayesNet, dataSet);
		} else if (_localMaximaEscapeMethod.equals("MultipleRestarts")) {
			ctp = multipleRestarts(bayesNet, dataSet);
		}

		//int nContinuedSteps = 0;
		// runs until convergence
		double loglikelihood;
		bayesNet = ctp.getBayesNet();
		do {
			loglikelihood = bayesNet.getLoglikelihood(dataSet);
			localEmStep(ctp, dataSet);
			_nSteps++;
			//nContinuedSteps++;
		} while (bayesNet.getLoglikelihood(dataSet) - loglikelihood > _threshold
				&& _nSteps < _nContinuedSteps);
				//&& nContinuedSteps < _nContinuedSteps);

		return bayesNet;
	}

	/**
	 * Runs one localEm step on the BN using the specified Ctp as the inference
	 * algorithm.
	 * 
	 * @param ctp
	 *            Inference engine.
	 * @param dataSet
	 *            data set to be used.
	 */
	private void localEmStep(CliqueTreePropagation ctp, DataSet dataSet) {
		BayesNet bayesNet = ctp.getBayesNet();
		HashMap<Variable, Function> suffStats = new HashMap<Variable, Function>();
		double loglikelihood = 0.0;

		// computes datum by datum
		for (DataCase dataCase : dataSet.getData()) {
			double weight = dataCase.getWeight();

			// copy Message to ctp.
//			CliqueTree ctInRepository = _repository.get(dataCase)
//					.getCliqueTree();
//			ctp.getCliqueTree().copyInMsgsFrom(ctInRepository);
			
			
			Set<MessagesForLocalEM> msgs = _repository.get(dataCase);
			ctp.getCliqueTree().copyInMsgsFrom(msgs);
			
			ctp.setEvidence(dataSet.getVariables(), dataCase.getStates());

			// propagates
			double likelihood = ctp.propagate();

			// updates sufficient statistics for each mutable node
			for (Variable var : _mutableVars) {
				Function fracWeight = ctp.computeFamilyBelief(var);
				fracWeight.multiply(weight);

				if (suffStats.containsKey(var)) {
					suffStats.get(var).plus(fracWeight);
				} else {
					suffStats.put(var, fracWeight);
				}
			}

			// updates loglikelihood
			loglikelihood += Math.log(likelihood) * weight;
		}

		// updates parameters
		for (Variable var : _mutableVars) {
			Function cpt = suffStats.get(var);
			cpt.normalize(var);
			bayesNet.getNode(var).setCpt(cpt);
		}

		// updates loglikelihood of argument BN
		bayesNet.setLoglikelihood(dataSet, loglikelihood);
	}

	/**
	 * Set the collection of mutable variables(BeliefNodes).
	 * 
	 * @param vars
	 */
	public void setMutableVars(Variable[] vars) {
		_mutableVars = vars;
	}

	/**
	 * Set the template clique tree propagation
	 * 
	 * @param templateCtp
	 */
	public void setTemplateCtp(CliqueTreePropagation templateCtp) {
		_templateCtp = templateCtp;
	}

	/**
	 * Set the message of repository. It is a mapping from every dataCase to a
	 * CliqueTreePropagation. Useful messages are stored in the CliqueNodes.
	 * When calling this method, make sure that the messages are properly set.
	 * 
	 * @param repository
	 */
	public void setRepository(Map<DataCase, Set<MessagesForLocalEM>> repository) {
		_repository = repository;
	}

	/**
	 * Replaces the maximum number of steps allowed in this EM algorithm.
	 * 
	 * @param nMaxSteps
	 *            new maximum number of steps.
	 */
	public final void setNumberOfContinuedSteps(int nContinuedSteps) {
		assert nContinuedSteps > 0;
		_nContinuedSteps = nContinuedSteps;
	}
	
	
}