/**
 * CliqueTreePropagation.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.reasoner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Variable;

/**
 * This class provides an implementation for clique tree propagation (CTP)
 * algorithm.
 * 
 * @author Yi Wang
 * 
 *         Be careful about the type of model. The method to construct the
 *         clique tree and the method to do the propagation for general bayesnet
 *         and for LTM are different.
 * 
 *         If you are working with non-LTM bayesnet, set the model as class
 *         "BayesNet"; otherwise, you can set it as class "LTM".
 * 
 * @author LIU Tengfei
 * 
 * 
 */
public final class CliqueTreePropagation implements Cloneable {

	/**
	 * The BN under query.
	 */
	private BayesNet _bayesNet;

	/**
	 * The CT used by this CTP.
	 */
	private CliqueTree _cliqueTree;

	/**
	 * 
	 */
	private Map<Variable, Integer> _evidence = new HashMap<Variable, Integer>();

	private double lastLogLikelihood = Double.NaN;

	/**
	 * Dummy constructor. It is supposed that only
	 * <code>CliqueTreePropagation.clone()</code> will invoke it.
	 */
	private CliqueTreePropagation() {
	}

	/**
	 * Constructs a CTP for the specified HLCM. The CliqueTree is the socalled
	 * natural clique tree for HLCM .
	 * 
	 * @param LTM
	 *            model under query.
	 */
	public CliqueTreePropagation(LTM model) {
		_bayesNet = model;
		_cliqueTree = new CliqueTree(model);
		_evidence = new HashMap<Variable, Integer>();
	}

	/**
	 * @param model
	 *            HLCM under query.
	 * @param families
	 *            To define the focusedsubtree.
	 * @param variables
	 *            To define the focusedsubtree.
	 */
	public CliqueTreePropagation(LTM model, Variable[] families,
			Variable[] variables) {
		_bayesNet = model;
		_cliqueTree = new CliqueTree(model, families, variables);
		_evidence = new HashMap<Variable, Integer>();
	}

	/**
	 * Constructs a CTP for the specified BN.
	 * 
	 * @param bayesNet
	 *            BN under query.
	 */
	public CliqueTreePropagation(BayesNet bayesNet) {
		_bayesNet = bayesNet;
		_cliqueTree = new CliqueTree(_bayesNet);
		_evidence = new HashMap<Variable, Integer>();
	}

	/**
	 * Clears the evidence entered into this inference engine.
	 */
	public void clearEvidence() {
		_evidence.clear();
	}

	/**
	 * Creates and returns a deep copy of this CTP.
	 * 
	 * @return A deep copy of this CTP.
	 */
	public CliqueTreePropagation clone() {
		CliqueTreePropagation copy = new CliqueTreePropagation();
		copy._bayesNet = _bayesNet;
		copy._cliqueTree = _cliqueTree.clone();
		// abandon eveidence
		return copy;
	}

	/**
	 * Prepares functions attached to cliques by <b>copying</b> CPTs and
	 * absorbing evidences.
	 * <p>
	 * Note: When an attached function is the same as one cpt rather than a
	 * funciont through projction of a cpt, we use the reference directly.
	 * Therefore be careful of updating the cpts of a BayesNet.
	 * </p>
	 */
	public void absorbEvidence() {

		for (AbstractNode node : _cliqueTree.getNodes()) {
			CliqueNode cNode = (CliqueNode) node;
			cNode.clearFunctions();
			cNode.clearQualifiedNeiMsgs();
			cNode.setMsgsProd(Function.createIdentityFunction());
		}

		LinkedHashMap<Variable, Function> functions =
				new LinkedHashMap<Variable, Function>();

		for (AbstractNode node : _bayesNet.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;
			Variable var = bNode.getVariable();

			CliqueNode familiyClique = _cliqueTree.getFamilyClique(var);
			if (familiyClique != null
					&& _cliqueTree.inFocusedSubtree(familiyClique))
				// initializes function as CPT
				functions.put(var, bNode.getCpt());
		}

		Set<Variable> mutableVars = functions.keySet();

		for (Variable var : _evidence.keySet()) {
			int value = _evidence.get(var);

			BeliefNode bNode = _bayesNet.getNodeByName(var.getName());

			if (mutableVars.contains(var)) {
				functions.put(var, functions.get(var).project(var, value));

			}
			if (bNode == null) {
				System.out.println("bNode == null, var.getName(): " + var.getName() + " value: " + value);
			}
			if (bNode.getChildren() == null) {
				System.out.println("bNode.getChildren() == null, var.getName(): " + var.getName() + " value: " + value);
			}
			for (DirectedNode child : bNode.getChildren()) {
				BeliefNode bChild = (BeliefNode) child;
				Variable varChild = bChild.getVariable();
				if (mutableVars.contains(varChild))
					functions.put(varChild,
							functions.get(varChild).project(var, value));
			}
		}

		for (Variable var : mutableVars) {
			// attaches function to family covering clique
			_cliqueTree.getFamilyClique(var).attachFunction(functions.get(var));
		}
	}

	/**
	 * @param source
	 * @param destination
	 * @param subtree
	 * @param standingNodes
	 * @return
	 */
	private Function collectMessage(CliqueNode source, CliqueNode destination,
			Set<CliqueNode> subtree, Collection<Variable> standingVars) {
		Function msg = Function.createIdentityFunction();

		// collects messages from neighbors of source except destination
		for (AbstractNode neighbor : source.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			if (clique != destination) {
				if (subtree.contains(clique)) {
					msg =
							msg.times(collectMessage(clique, source, subtree,
									standingVars));
				} else {
					msg = msg.times(clique.getMessageTo(source));
				}
			}
		}

		// times up all functions in source
		for (Function func : source.getFunctions()) {
			msg = msg.times(func);
		}

		// sums out difference between source and destination but retain
		// standing nodes
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!_evidence.containsKey(var) && !standingVars.contains(var)) {
				msg = msg.sumOut(var);
			}
		}

		return msg;
	}

	/**
	 * Returns the posterior probability distribution of the specified variable
	 * 
	 * @param var
	 *            Variable under query.
	 * @return The posterior probability distribution of the specified variable.
	 */
	public Function computeBelief(Variable var) {
		// associated BN must contain var
		BeliefNode node = _bayesNet.getNode(var);
		assert node != null;

		Function belief = null;

		if (_evidence.containsKey(var)) {
			// likelihood must be positive
			assert computeLikelihood() > 0.0;
			belief = Function.createIndicatorFunction(var, _evidence.get(var));
		} else {
			// initialization
			belief = Function.createIdentityFunction();

			// computes potential at answer extraction clique
			CliqueNode answerClique = _cliqueTree.getFamilyClique(var);
			
			// times up functions attached to answer extraction clique
			
			if (node.isRoot()) {
				if (answerClique == null) {
					System.out.println("is Root name: " + var.getName() + " function size: null answerClique == null");
				}
			}
			for (Function function : answerClique.getFunctions()) {
				if (function == null) {
					System.out.println("function == null");
				}
				belief = belief.times(function);
			}

			// times up messages to answer extraction clique
			for (AbstractNode neighbor : answerClique.getNeighbors()) {
				if (neighbor == null) {
					System.out.println("neighbor == null");
				}
				if (((CliqueNode) neighbor).getMessageTo(answerClique) == null) {
					System.out.println("getMessageTo == null");
				}
				belief =
						belief.times(((CliqueNode) neighbor).getMessageTo(answerClique));
			}

			// marginalizes potential
			belief = belief.marginalize(var);

			// normalizes potential
			belief.normalize();
		}

		return belief;
	}

	/**
	 * Returns the posterior probability distribution of the specified
	 * collection of variable. The difference bwteen this method and
	 * <code> computeBelief(Collection<Variable> var)</code> is that here the
	 * Clique Subtree used for computing suffucient statistics is specified.
	 * 
	 * @param vars
	 *            Collection of variables under query.
	 * @param subtree
	 *            The clique subTree used for inference.
	 * @return The posterior probability distribution of the specified
	 *         collection of variables.
	 */
	public Function computeBelief(Collection<Variable> vars,
			Set<CliqueNode> subtree) {
		assert !vars.isEmpty();
		assert _bayesNet.containsVars(vars);

		// collects hidden and observed variables in query nodes
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		for (Variable var : vars) {
			if (_evidence.containsKey(var)) {
				obsVars.add(var);
				obsVals.add(_evidence.get(var));
			} else {
				hdnVars.add(var);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// constructs the minimal subtree that covers all query nodes
		// Set<CliqueNode> subtree = _cliqueTree.computeMinimalSubtree(nodes);
		// Set<CliqueNode> subtree =
		// _cliqueTree.computeMinimalSubtree(hdnNodes);

		// uses first clique in the subtree as the pivot
		CliqueNode pivot = subtree.iterator().next();

		// computes the local potential at the pivot clique
		for (Function func : pivot.getFunctions())
			hdnBel = hdnBel.times(func);

		// collects messages from all neighbors of pivot and times them up
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			// message from neighbor
			if (subtree.contains(clique)) {
				// recollects message
				hdnBel =
						hdnBel.times(collectMessage(clique, pivot, subtree,
								vars));
			} else {
				// reuses original message
				hdnBel = hdnBel.times(clique.getMessageTo(pivot));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the posterior probability distribution of the specified
	 * collection of variables.
	 * 
	 * @param vars
	 *            Collection of variables under query.
	 * @return The posterior probability distribution of the specified
	 *         collection of variables.
	 */
	public Function computeBelief(Collection<Variable> vars) {
		assert !vars.isEmpty();
		assert _bayesNet.containsVars(vars);

		// collects hidden and observed variables in query nodes
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		for (Variable var : vars) {
			if (_evidence.containsKey(var)) {
				obsVars.add(var);
				obsVals.add(_evidence.get(var));
			} else {
				hdnVars.add(var);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// constructs the minimal subtree that covers all query nodes
		// Set<CliqueNode> subtree = _cliqueTree.computeMinimalSubtree(nodes);
		Set<CliqueNode> subtree = _cliqueTree.computeMinimalSubtree(hdnVars);

		// uses first clique in the subtree as the pivot
		CliqueNode pivot = subtree.iterator().next();

		// computes the local potential at the pivot clique
		for (Function func : pivot.getFunctions()) {
			hdnBel = hdnBel.times(func);
		}

		// collects messages from all neighbors of pivot and times them up
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			// message from neighbor
			if (subtree.contains(clique)) {
				// recollects message
				hdnBel =
						hdnBel.times(collectMessage(clique, pivot, subtree,
								vars));
			} else {
				// reuses original message
			
				hdnBel = hdnBel.times(clique.getMessageTo(pivot));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the posterior probability distribution of the family of the
	 * specified variable. It is a function of all Variables in the family no
	 * matter it is observed or hidden.
	 * 
	 * @param var
	 *            variable under query.
	 * @return the posterior probability distribution of the family of the
	 *         specified variable.
	 */
	public Function computeFamilyBelief(Variable var) {
		// associated BN must contain var
		assert _bayesNet.containsVar(var);

		// collects hidden and observed variables in family
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		if (_evidence.containsKey(var)) {
			obsVars.add(var);
			obsVals.add(_evidence.get(var));
		} else {
			hdnVars.add(var);
		}

		BeliefNode node = _bayesNet.getNode(var);
		for (AbstractNode parent : node.getParents()) {
			BeliefNode bParent = (BeliefNode) parent;
			Variable vParent = bParent.getVariable();

			if (_evidence.containsKey(vParent)) {
				obsVars.add(vParent);
				obsVals.add(_evidence.get(vParent));
			} else {
				hdnVars.add(vParent);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// computes potential at family covering clique
		CliqueNode familyClique = _cliqueTree.getFamilyClique(var);

		// times up functions attached to family covering clique
		if (familyClique == null) {
			System.out.println("familyClique == null in computeFamilyBelief");
		}
		for (Function function : familyClique.getFunctions()) {
			if (function == null) {
				System.out.println("function == null in computeFamilyBelief");
			}
			hdnBel = hdnBel.times(function);
		}

		// (In the HLCM propogation case)After this, the hdnBel is superior to
		// any funtion multiplied.

		if (_bayesNet instanceof LTM) {
			// times up messages to family covering clique
			hdnBel.multiply(familyClique._msgsProd);
			Set<CliqueNode> already = familyClique._qualifiedNeiMsgs;
			for (AbstractNode neighbor : familyClique.getNeighbors()) {
				if (!already.contains(neighbor)) {
					if (neighbor == null) {
						System.out.println("neighbor == null in computeFamilyBelief");
					}
					Function function1 = ((CliqueNode) neighbor).getMessageTo(familyClique);
					if (function1 == null) {
						System.out.println("function1 == null in computeFamilyBelief");
					}
					hdnBel.multiply(function1);
				}
			}
		} else {
			for (AbstractNode neighbor : familyClique.getNeighbors()) {
				hdnBel =
						hdnBel.times(((CliqueNode) neighbor).getMessageTo(familyClique));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the likelihood of the evidences on the associated BN. Make sure
	 * that propogation has been conducted when calling this method.
	 */
	public double computeLikelihood() {
		CliqueNode pivot = _cliqueTree.getPivot();

		// times up functions attached to pivot
		Function potential = Function.createIdentityFunction();
		for (Function function : pivot.getFunctions()) {
			potential = potential.times(function);
		}

		// times up messages to pivot
		double normalization = 1.0;
		double logNormalization = 0;
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;
			potential = potential.times(clique.getMessageTo(pivot));
			normalization *= clique.getNormalizationTo(pivot);
			logNormalization += clique.getLogNormalizationTo(pivot);
		}

		double n = potential.sumUp();
		lastLogLikelihood = logNormalization + Math.log(n);
		return n * normalization;
	}

	/**
	 * Returns the last log-likelihood computed. It is updated after each call
	 * of {@link computeLikelihood}.
	 * 
	 * @return last log-likelihood computed
	 */
	public double getLastLogLikelihood() {
		return lastLogLikelihood;
	}

	/**
	 * Collects messages around the source and sends an aggregated message to
	 * the destination.
	 * 
	 * @param source
	 *            source around which messages are to be collected.
	 * @param destination
	 *            destination to which an aggregated message is to be sent.
	 */
	public void collectMessage(CliqueNode source, CliqueNode destination) {
		if (source.getMessageTo(destination) == null
				|| _cliqueTree.inFocusedSubtree(source)) {
			// collects messages from neighbors of source except destination
			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					collectMessage((CliqueNode) neighbor, source);
				}
			}

			// When I try to merge the code of different versions, I find that
			// some version only deals with LTM and the other deal with general
			// bayesNet.
			// So I merge them here. liutf
			if (_bayesNet instanceof LTM) {
				sendMessage4HLCM(source, destination);
				Function func = source.getMessageTo(destination);

				if (!func.hasZeroCell()) {
					destination.modifyMsgProd(func);
					destination._qualifiedNeiMsgs.add(source);
				}
			} else {
				sendMessage(source, destination);
			}
		}
	}

	/**
	 * Sends an aggregated message from the source to the destination and
	 * distributes the message around the destination.
	 * 
	 * @param source
	 * @param destination
	 */
	public void distributeMessage(CliqueNode source, CliqueNode destination) {
		if (_cliqueTree.inFocusedSubtree(destination)) {

			// same reason as in function "collectMessage"
			if (_bayesNet instanceof LTM) {
				sendMessage4HLCM(source, destination);
				Function func = source.getMessageTo(destination);
				if (!func.hasZeroCell()) {
					destination.modifyMsgProd(func);
					destination._qualifiedNeiMsgs.add(source);
				}
			} else {
				sendMessage(source, destination);
			}

			// distributes messages to neighbors of destination except source
			for (AbstractNode neighbor : destination.getNeighbors()) {
				if (neighbor != source) {
					distributeMessage(destination, (CliqueNode) neighbor);
				}
			}
		}
	}

	/**
	 * Returns the BN that is associated with this CTP.
	 * 
	 * @return the BN that is associated with this CTP.
	 */
	public BayesNet getBayesNet() {
		return _bayesNet;
	}

	/**
	 * Get _cliqueTree
	 * 
	 * @author csct
	 * @return _cliqueTree
	 */
	public CliqueTree getCliqueTree() {
		return _cliqueTree;
	}

	/**
	 * Propagates messages on the CT.
	 * 
	 * @return LL.
	 */
	public double propagate() {
		if (Thread.interrupted()) {
			throw new RuntimeException("Thread interrupted");
		}
		
		// absorbs evidences
		absorbEvidence();

		CliqueNode pivot = _cliqueTree.getPivot();

		// collects messages from neighbors of pivot
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			collectMessage((CliqueNode) neighbor, pivot);
		}

		// distributes messages to neighbors of pivot
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			distributeMessage(pivot, (CliqueNode) neighbor);
		}

		return computeLikelihood();
	}
	
	/**
	 * Sends a message from the source to the destination.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage4HLCM(CliqueNode source, CliqueNode destination) {

		Collection<CliqueNode> variableCliques =
				_cliqueTree._variableCliques.values();

		Function message = null;
		double normalization = 1.0;
		double logNormalization = 0;

		if (variableCliques.contains(source)) {
			Set<CliqueNode> already = source._qualifiedNeiMsgs;

			Variable var = source.getVariables().iterator().next();

			if (_evidence.containsKey(var)) {
				// otherwise, when latent variable is listed in evidence
				// variables, there will be a bug.
				message = Function.createIdentityFunction();
			} else {
				message = Function.createIdentityFunction(var);
			}

			message.multiply(source._msgsProd);

			for (AbstractNode neighbor : source.getNeighbors()) {
				CliqueNode cNeighbor = (CliqueNode) neighbor;

				if (cNeighbor != destination) {
					normalization *= cNeighbor.getNormalizationTo(source);
					logNormalization += cNeighbor.getLogNormalizationTo(source);

					if (!already.contains(cNeighbor)) {
						message.multiply(cNeighbor.getMessageTo(source));
					}
				} else {
					if (already.contains(cNeighbor))
						message.divide(cNeighbor.getMessageTo(source));
				}
			}

			for (Function function : source.getFunctions()) {
				message.multiply(function);
			}
		} else {
			message = Function.createIdentityFunction();
			for (Function function : source.getFunctions()) {
				message = message.times(function);
			}
			// After this message is superior to...

			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					CliqueNode clique = (CliqueNode) neighbor;
					message.multiply(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
					logNormalization += clique.getLogNormalizationTo(source);
				}
			}

		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!_evidence.containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		double n = message.normalize();
		normalization *= n;
		logNormalization += Math.log(n);

		assert normalization >= Double.MIN_NORMAL;

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);
		source.setLogNormalizationTo(destination, logNormalization);
	}

	/**
	 * Sends a message from the source to the destination.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage4HLCM_OLDVERSIONwithoutDivision(CliqueNode source,
			CliqueNode destination) {

		Collection<CliqueNode> variableCliques =
				_cliqueTree._variableCliques.values();

		Function message = null;
		double normalization = 1.0;

		if (variableCliques.contains(source)) {
			Variable var = source.getVariables().iterator().next();
			message = Function.createIdentityFunction(var);

			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					CliqueNode clique = (CliqueNode) neighbor;
					// NOte
					message.multiply(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
				}
			}

			for (Function function : source.getFunctions()) {
				// Note
				message.multiply(function);
			}

		} else {
			message = Function.createIdentityFunction();

			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					CliqueNode clique = (CliqueNode) neighbor;
					message = message.times(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
				}
			}

			for (Function function : source.getFunctions()) {
				message = message.times(function);
			}
		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!_evidence.containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		normalization *= message.normalize();

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);

	}

	/**
	 * Sends a message from the source to the destiation.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage(CliqueNode source, CliqueNode destination) {
		Function message = Function.createIdentityFunction();
		double normalization = 1.0;
		double logNormalization = 0;

		for (AbstractNode neighbor : source.getNeighbors()) {
			if (neighbor != destination) {
				CliqueNode clique = (CliqueNode) neighbor;
				message = message.times(clique.getMessageTo(source));
				normalization *= clique.getNormalizationTo(source);
				logNormalization += clique.getLogNormalizationTo(source);
			}
		}

		for (Function function : source.getFunctions()) {
			message = message.times(function);
		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!_evidence.containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		double n = message.normalize();
		normalization *= n;
		logNormalization += Math.log(n);

		assert normalization >= Double.MIN_NORMAL;

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);
		source.setLogNormalizationTo(destination, logNormalization);
	}

	public void setEvidence(Variable[] variables, int[] states) {
		assert variables.length == states.length;

		_evidence.clear();

		for (int i = 0; i < variables.length; i++) {
			// ignore this variable if its value is missing
			if (states[i] == DataSet.MISSING_VALUE)
				continue;

			Variable var = variables[i];

			assert _bayesNet.containsVar(var);
			assert variables[i].isValid(states[i]);

			_evidence.put(var, states[i]);
		}
	}

	public void setBayesNet(BayesNet bayesNet) {
		_bayesNet = bayesNet;
	}

	public void addEvidence(Variable variable, int state) {
		BeliefNode node = _bayesNet.getNode(variable);

		assert node != null;

		assert variable.isValid(state);

		if (state == DataSet.MISSING_VALUE) {
			_evidence.remove(node);
		} else {
			_evidence.put(node.getVariable(), state);
		}
	}

	public int getEvidence(Variable variable) {
		BeliefNode node = _bayesNet.getNode(variable);

		assert node != null;

		if (_evidence.containsKey(node)) {
			return _evidence.get(node);
		} else {
			return DataSet.MISSING_VALUE;
		}
	}
}