/**
 * BeliefNode.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.latlab.graph.AbstractGraph;
import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.util.Function;
import org.latlab.util.Variable;

/**
 * This class provides an implementation for nodes in BNs.
 * 
 * @author Yi Wang
 * 
 */
public class BeliefNode extends DirectedNode {

	/**
	 * the variable attached to this node.
	 */
	private Variable _variable;

	/**
	 * the conditional probability table (CPT) attached to this node.
	 */
	private Function _cpt;

	/**
	 * We replace the variable in the node. Also we dp the replacement for the
	 * conditional probability table if any.
	 * 
	 */
	public void replaceVar(Variable newVar) {
		if (_cpt != null) {
			_cpt = _cpt.replaceVar(_variable, newVar);
		}
		_variable = newVar;
	}

	/**
	 * <p>
	 * Constructs a node with the specified variable attached and the specified
	 * graph to contain it. This node has the same name as the argument
	 * variable.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Besides constructors of subclasses, only
	 * <code>BayesNet.addNode(Variable)</code> is supposed call this method.
	 * </b>
	 * </p>
	 * 
	 * @param graph
	 *            graph to contain this node.
	 * @param variable
	 *            variable to be attached to this node.
	 * @see BayesNet#addNode(Variable)
	 */
	protected BeliefNode(AbstractGraph graph, Variable variable) {
		super(graph, variable.getName());

		_variable = variable;

		// sets CPT as uniform distribution
		_cpt = Function.createUniformDistribution(variable);
	}

	/**
	 * <p>
	 * Attachs the specified incoming edge to this node. This implementation
	 * extends <code>DirectedNode.attachInEdge(Edge edge)</code> such that the
	 * CPT of this node will be updated as well.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>BayesNet.addEdge(AbstractNode, AbstractNode)</code>
	 * is supposed to call this method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            incoming edge to be attached to this node.
	 * @see BayesNet#addEdge(AbstractNode, AbstractNode)
	 */
	@Override
	protected final void attachInEdge(Edge edge) {
		super.attachInEdge(edge);

		// new CPT should include variable attached to parent
		_cpt = _cpt.addVariable(((BeliefNode) edge.getTail()).getVariable());
	}

	/**
	 * Returns the standard dimension, namely, the number of free parameters in
	 * the CPT, of this node.
	 * 
	 * @author csct
	 * @return the standard dimension of this node.
	 */
	public final int computeDimension() {
		// let X and pi(X) be variable attached to this node and joint variable
		// attached to parents, respectively. the standard dimension equals
		// (|X|-1)*|pi(X)|.
		int dimension = _variable.getCardinality() - 1;

		for (DirectedNode parent : getParents()) {
			dimension *= ((BeliefNode) parent).getVariable().getCardinality();
		}

		return dimension;
	}

	/**
	 * Suppose this is a Latent node in an HLCM. Then for the purpose of
	 * satisfying regularity, the cardinality of this node is at most:
	 * CardOfNeighbor1 x CardOFNeighbor2 x ... x CardOfNeighborN / The maximum
	 * cardinality amony its N neighbors. This method compute this quantity for
	 * check regularity.
	 * 
	 * @return The maximum possible cardinality of this node in an HLCM
	 */
	public final int computeMaxPossibleCardInHLCM() {

		int product = 1;
		int max = 1;
		for (AbstractNode neighbor : getNeighbors()) {
			int neighborCard = ((BeliefNode) neighbor).getVariable()
					.getCardinality();
			product *= neighborCard;
			max = max < neighborCard ? neighborCard : max;
		}

		if (product < 0)
			return Integer.MAX_VALUE;
		else
			return product / max;
	}

	/**
	 * <p>
	 * Detachs the specified incoming edge from this node. This implementation
	 * extends <code>DirectedNode.detachInEdge(Edge)</code> such that the CPT
	 * will be updated as well.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>BayesNet.removeEdge(Edge)</code> is supposed to call
	 * this method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            incoming edge to be detached from this node.
	 * @see BayesNet#removeEdge(Edge)
	 */
	@Override
	protected final void detachInEdge(Edge edge) {
		super.detachInEdge(edge);

		// new CPT should exclude variable attached to old parent
		_cpt = _cpt.removeVariable(((BeliefNode) edge.getTail()).getVariable());
	}

	/**
	 * Returns the CPT attached to this node. For the sake of efficiency, this
	 * implementation returns the reference to a private field. Make sure you
	 * understand this before using this method.
	 * 
	 * @return the CPT attached to this node.
	 */
	public final Function getCpt() {
		return _cpt;
	}

	/**
	 * Returns the variable attached to this node. For the sake of efficiency,
	 * this implementation returns the reference to a private field. Make sure
	 * you understand this before using this method.
	 * 
	 * @return the variable attached to this node.
	 */
	public final Variable getVariable() {
		return _variable;
	}
	
	/**
	 * Returns the child variables of this node. For the sake of efficiency,
	 * this implementation returns the reference to a private field. Make sure
	 * you understand this before using this method.
	 * @author pchenac
	 * @return the variable attached to this node.
	 */
	
	
	public final ArrayList<Variable> getChildrenVars() {
		ArrayList<Variable> children = new ArrayList<Variable>();
		for(DirectedNode n: _children.keySet()){
			children.add(((BeliefNode)n).getVariable());
			
		}
		return children;
	}
	
	
	/**
	 * Returns the latent variables among child variables of this node. For the sake of efficiency,
	 * this implementation returns the reference to a private field. Make sure
	 * you understand this before using this method.
	 * @author pchenac
	 * @return the variable attached to this node.
	 */
	
	
	public final ArrayList<Variable> getChildrenLatVars() {
		ArrayList<Variable> children = new ArrayList<Variable>();
		for(DirectedNode n: _children.keySet()){
			if(!((BeliefNode)n).isLeaf()){
			children.add(((BeliefNode)n).getVariable());
			}
		}
		return children;
	}
	
	
	public final ArrayList<Variable> getChildrenManiVars() {
		ArrayList<Variable> children = new ArrayList<Variable>();
		for(DirectedNode n: _children.keySet()){
			if(((BeliefNode)n).isLeaf()){
			children.add(((BeliefNode)n).getVariable());
			}
		}
		return children;
	}
	/**
	 * This method returns a parent of this node. If the node is a root, return
	 * null. When we call this method for a node in an HLCM in which every
	 * non-root node has only one parent, the output is certain.
	 * 
	 * @author revised by Peixian
	 * @return One Parent of this node. Null if this is a root.
	 */
	public BeliefNode getParent() {
		if (isRoot()) {
			return null;
		}

		return (BeliefNode) getParents().iterator().next();
	}

	/**
	 * Returns <code>true</code> if the specified function can be a valid CPT of
	 * this node. Here the meaning of <b>valid</b> is kind of partial since we
	 * only check this function is a function of this node variable and the
	 * variables in its parents. However <b>validity</b> do Not guarantee that
	 * this function is a conditional probability table of
	 * <code>this._variable</code>
	 * 
	 * @param function
	 *            function whose validity as a CPT is to be tested.
	 * @return <code>true</code> if the specified function can be a valid CPT of
	 *         this node.
	 */
	public final boolean isValidCpt(Function function) {
		// valid CPT must contain exactly variables in this family
		if (function.getDimension() != getInDegree() + 1) {
			return false;
		}

		if (!function.contains(_variable)) {
			return false;
		}

		for (DirectedNode parent : getParents()) {
			if (!function.contains(((BeliefNode) parent).getVariable())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * <p>
	 * Randomly sets the parameters of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>BayesNet.randomlyParameterize()</code> and
	 * <code>BayesNet.randomlyParameterize(java.util.Collection)</code> are
	 * supposed to call this method.
	 * </p>
	 * 
	 * <p>
	 * Notes add by csct: We consider a more general situation of calling this
	 * method, that is, when variables contained in "_ctp" mismatch with the
	 * family Variables of the node, or maybe even the "_ctp" is just null. The
	 * cases often occur when structure learning, e.g. in
	 * HLCM.introduceState4Root().
	 * </p>
	 * 
	 * @see BayesNet#randomlyParameterize()
	 * @see BayesNet#randomlyParameterize(java.util.Collection)
	 */
	protected final void randomlyParameterize() {

		_cpt.randomlyDistribute(_variable);
	}

	/**
	 * Replaces the CPT attached to this node. This implementation will check
	 * whether this Function cpt is a funtion of this node and its parent nodes.
	 * However, it is not guaranteed that cpt satisfies the probability
	 * constraint. Therefore, when use this method, make sure Function cpt is in
	 * the form of a conditional probability of <code>this._variable</code>
	 * 
	 * @param cpt
	 *            new CPT to be attached to this node.
	 */
	public final void setCpt(Function cpt) {
		// CPT must be valid
		assert isValidCpt(cpt);

		_cpt = cpt;

		// loglikelihoods expire
		((BayesNet) _graph).expireLoglikelihoods();
	}

	/**
	 * <p>
	 * Replaces the name of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Do NOT use this method if the BN that contains this node has
	 * been cloned. Otherwise, in the copy, names of node and its attached
	 * variable will be inconsistent in the copy. </b>
	 * </p>
	 * 
	 * @param name
	 *            new name of this node.
	 */
	@Override
	public final void setName(String name) {
		super.setName(name);

		// renames attached variable
		_variable.setName(name);
	}

	/**
	 * Returns a list containing the variables of this belief node and its
	 * parent nodes.
	 * 
	 * @return list containing node variable and parent variables.
	 */
	public ArrayList<Variable> getNodeAndParentVariables() {
		ArrayList<Variable> list = new ArrayList<Variable>(
				getParents().size() + 1);

		list.add(getVariable());

		Collection<DirectedNode> parentNodes = getParents();
		for (DirectedNode parentNode : parentNodes) {
			BeliefNode beliefNode = (BeliefNode) parentNode;
			list.add(beliefNode.getVariable());
		}

		return list;
	}
	
	/**
	 * Reorders the states of the variable of this node.  
	 * It affects also the probability table of its child nodes.
	 * @{code order[i] == x} means that the state originally at x should
	 * now be put at i.
	 * @param order
	 */
	public void reorderStates(int[] order) {
		_variable.reorderStates(order);
		
		_cpt.reorderStates(_variable, order);
		
		for (DirectedNode o : getChildren()) {
			BeliefNode node = (BeliefNode) o;
			node._cpt.reorderStates(_variable, order);
		}
	}
}
