/**
 * CliqueTree.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.reasoner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import org.latlab.graph.*;
import org.latlab.util.*;
import org.latlab.model.*;

/**
 * This class provides an implementation for clique trees (CTs).
 * 
 * @author Yi Wang
 * 
 */
public class CliqueTree extends UndirectedGraph {

	/**
	 * the pivot of this CT. likelihood will be computed at the pivot.
	 */
	private CliqueNode _pivot;

	/**
	 * the collection of cliques in the subtree in which full propagation will
	 * be carried out. All the nodes must be continuous.
	 */
	protected Set<CliqueNode> _focusedSubtree;

	/**
	 * Manually set the _focusedSubtree
	 * 
	 * @param focusedSubtree
	 */
	public void setFocusedSubtree(Set<CliqueNode> focusedSubtree) {
		_focusedSubtree = focusedSubtree;
	}

	/**
	 * This is exclusively for HLCM. For general BayesNet, it is null.
	 */
	protected Map<Variable, CliqueNode> _variableCliques;

	/**
	 * the map from belief nodes to family covering cliques. belief for each
	 * node and its family will be computed at its family covering clique. Note
	 * that in the clique tree constructed for HLCM, the corresponding clique
	 * node is the edge clique node as well.
	 */
	protected Map<Variable, CliqueNode> _familyCliques;

	/**
	 * <p>
	 * Constructs an empty CT. We have NOT construct the _familyCliques and
	 * _focusedSubtree fields.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>clone()</code> is supposed to call this method. </b>
	 * </p>
	 */
	private CliqueTree() {
		super();
	}

	/**
	 * Constructs the natural clique tree for HLCM. For root node, the family
	 * clique is the corresponding separator. For others, the family clique is
	 * the corresponding edge clique node. The pivot is the root separator.
	 */
	public CliqueTree(LTM model) {
		super();

		_familyCliques = new HashMap<Variable, CliqueNode>();
		_variableCliques = new HashMap<Variable, CliqueNode>();

		// Construct variable clique nodes
		for (Variable var : model.getLatVars()) {
			LinkedHashSet<Variable> varSet = new LinkedHashSet<Variable>();
			varSet.add(var);
			CliqueNode cliqueNode = addNode(varSet, null);
			_variableCliques.put(var, cliqueNode);
		}

		// Build connections
		for (Edge edge : model.getEdges()) {
			Variable head = ((BeliefNode) edge.getHead()).getVariable();
			Variable tail = ((BeliefNode) edge.getTail()).getVariable();
			LinkedHashSet<Variable> varSet = new LinkedHashSet<Variable>();
			// The order is significant
			varSet.add(head);
			varSet.add(tail);
			CliqueNode Clique = addNode(varSet, null);
			addEdge(Clique, _variableCliques.get(tail));
			if (_variableCliques.get(head) != null)
				addEdge(Clique, _variableCliques.get(head));
			_familyCliques.put(head, Clique);
		}

		Variable root = model.getRoot().getVariable();
		CliqueNode rootClique = _variableCliques.get(root);
		_familyCliques.put(root, rootClique);

		_pivot = rootClique;
	}

	/**
	 * Constructs a CT for the specified BN. TODO I have Checked this method.
	 * However, for current work, this method is not used since we are dealing
	 * with the HLCM for now. We may delete this method or revise it in the
	 * present of new demand.
	 * 
	 * @param bayesNet
	 *            BN to be associated with this CT.
	 */
	public CliqueTree(BayesNet bayesNet) {

		// I know this can be implicit. However, I am inclined to add it.
		super();

		// computes minimum deficiency order
		UndirectedGraph moralGraph = bayesNet.computeMoralGraph();
		LinkedList<String> order = moralGraph.minimumDeficiencySearch();

		// builds this CT
//		buildCliqueTree(moralGraph, order.iterator(), bayesNet);
		buildCliqueTreeNotRecursively(moralGraph, order.iterator(), bayesNet);

		// finds family covering cliques for belief nodes
		_familyCliques = new HashMap<Variable, CliqueNode>();

		for (Variable var : bayesNet.getVariables()) {
			// finds smallest clique that covers this family
			int minCard = Integer.MAX_VALUE;
			CliqueNode familyClique = null;

			ArrayList<Variable> vParents = new ArrayList<Variable>();
			for (DirectedNode node : bayesNet.getNode(var).getParents()) {
				vParents.add(((BeliefNode) node).getVariable());
			}

			for (AbstractNode clique : _nodes) {
				CliqueNode cliqueNode = (CliqueNode) clique;
				int card = cliqueNode.getCardinality();

				if (card < minCard && cliqueNode.contains(var)
						&& cliqueNode.containsAll(vParents)) {
					minCard = card;
					familyClique = cliqueNode;
				}
			}

			_familyCliques.put(var, familyClique);
		}

		// sets smallest clique as pivot for fast likelihood computation
		int minCard = Integer.MAX_VALUE;
		for (AbstractNode node : _nodes) {
			CliqueNode clique = (CliqueNode) node;
			int card = clique.getCardinality();

			if (card < minCard) {
				minCard = card;
				_pivot = clique;
			}
		}
	}

	/**
	 * Construct the corresponding CliqueTree with the focusedSubstree
	 * specified. When using this method, make sure that the resulting subtree
	 * is contigious. Pivot is an arbitrary clique in the subtree.
	 * 
	 * @param model
	 * @param families
	 *            The corresponding familiy covering clique of these nodes is
	 *            included in the focusedSubtree.
	 * @param variables
	 *            The corresponding variable cliques are included in the
	 *            focusedSubtree.
	 */
	public CliqueTree(LTM model, Variable[] families, Variable[] variables) {
		this(model);
		assert variables.length != 0 || families.length != 0;
		_focusedSubtree = new LinkedHashSet<CliqueNode>();
		for (Variable var : variables) {
			if (_variableCliques.containsKey(var))
				_focusedSubtree.add(_variableCliques.get(var));
		}
		for (Variable var : families) {
			_focusedSubtree.add(_familyCliques.get(var));
		}
		_pivot = _focusedSubtree.iterator().next();
	}

	/**
	 * Adds a clique that covers the specified collection of Variables to this
	 * clique tree.
	 * 
	 * @param vars
	 *            Collection of Variables to be attached to the clique.
	 * @return The clique that was added to this CT.
	 */
	private CliqueNode addNode(LinkedHashSet<Variable> vars, String name) {
		// creates clique
		CliqueNode node = null;
		if (name == null)
			node = new CliqueNode(this, vars);
		else
			node = new CliqueNode(this, vars, name);
		// adds clique to cliques in CTs
		_nodes.add(node);
		// maps name to clique
		_names.put(node.getName(), node);
		return node;
	}

	/**
	 * Recursively builds this CT.
	 * 
	 * @param moralGraph
	 *            Moral graph of the remainder of the associated BN.
	 * @param nameIter
	 *            Name of the next node to be eliminated from the moral graph.
	 */
	private void buildCliqueTree(UndirectedGraph moralGraph,
			Iterator<String> nameIter, BayesNet bayesNet) {
		// node to be eliminated
		AbstractNode elimNode = moralGraph.getNode(nameIter.next());

		// belief nodes to be attached to new clique
		LinkedHashSet<Variable> vars = new LinkedHashSet<Variable>();

		// node to be eliminated will be attached to new clique
		BeliefNode bNode = (BeliefNode) bayesNet.getNode(elimNode.getName());
		vars.add(bNode.getVariable());

		// separator (neighbors of eliminated node) will be attached to clique
		LinkedList<Variable> separator = new LinkedList<Variable>();
		for (AbstractNode neighbor : elimNode.getNeighbors()) {
			BeliefNode bNeighbor = (BeliefNode) bayesNet.getNode(neighbor
					.getName());

			separator.add(bNeighbor.getVariable());
			vars.add(bNeighbor.getVariable());
		}

		if (vars.size() == moralGraph.getNumberOfNodes()) {
			// ends recursion and adds clique to this CT
			addNode(vars, null);
		} else {
			// eliminates node
			moralGraph.eliminateNode(elimNode);

			// recursively builds rest of this CT
			buildCliqueTree(moralGraph, nameIter, bayesNet);

			// finds another clique in rest of CT that contains separator
			CliqueNode clique2 = null;
			for (AbstractNode node : _nodes) {
				clique2 = (CliqueNode) node;

				if (clique2.containsAll(separator)) {
					break;
				}
			}

			// adds clique to clique tree
			CliqueNode clique = addNode(vars, null);

			if (clique.containsAll(clique2.getVariables())) {
				// subsumes non-maximal clique
				for (AbstractNode neighbor : clique2.getNeighbors()) {
					addEdge(clique, neighbor);
				}

				removeNode(clique2);
			} else {
				// connects two cliques
				addEdge(clique, clique2);
			}
		}
	}
	
	/**
	 * builds this CT.
	 * 
	 * The recursive method will cause "stack over flow" when the tree is large.
	 * 
	 * @param moralGraph
	 *            Moral graph of the remainder of the associated BN.
	 * @param nameIter
	 *            Name of the next node to be eliminated from the moral graph.
	 */
	private void buildCliqueTreeNotRecursively(UndirectedGraph moralGraph, Iterator<String> nameIter, BayesNet bayesNet) 
	{
		
		ArrayList<CliqueNode> cliqueList = new ArrayList<CliqueNode>();
		ArrayList<LinkedList<Variable>> seperatorList = new ArrayList<LinkedList<Variable>>();
		
		while(nameIter.hasNext())
		{
			// node to be eliminated
			AbstractNode elimNode = moralGraph.getNode(nameIter.next());
			
			// belief nodes to be attached to new clique
			LinkedHashSet<Variable> vars = new LinkedHashSet<Variable>();

			// node to be eliminated will be attached to new clique
			BeliefNode bNode = (BeliefNode) bayesNet.getNode(elimNode.getName());
			vars.add(bNode.getVariable());
			
			// separator (neighbors of eliminated node) will be attached to clique
			LinkedList<Variable> separator = new LinkedList<Variable>();
			for (AbstractNode neighbor : elimNode.getNeighbors()) {
				BeliefNode bNeighbor = (BeliefNode) bayesNet.getNode(neighbor
						.getName());

				separator.add(bNeighbor.getVariable());
				vars.add(bNeighbor.getVariable());
			}
			
			// adds clique to clique tree
			CliqueNode clique = addNode(vars, null);
			
			cliqueList.add(clique);
			seperatorList.add(separator);
			
			if (vars.size() == moralGraph.getNumberOfNodes()) {
				// ends recursion and adds clique to this CT			
				break;
			} 
			
			// eliminates node
			moralGraph.eliminateNode(elimNode);
		}
		
		int size = cliqueList.size();
		
		for(int i=size-2; i>-1; i--)
		{

			CliqueNode clique = cliqueList.get(i);
			LinkedList<Variable> separator = seperatorList.get(i);
			
			// finds another clique in rest of CT that contains separator
			CliqueNode clique2 = null;
			for(int j=i+1; j<size; j++)
			{
				clique2 = cliqueList.get(j);
				
				if (clique2.containsAll(separator)) {
					break;
				}	
			}

			if (clique.containsAll(clique2.getVariables())) {
				// subsumes non-maximal clique
				for (AbstractNode neighbor : clique2.getNeighbors()) {
					addEdge(clique, neighbor);
				}

				removeNode(clique2);
			} else {
				// connects two cliques
				addEdge(clique, clique2);
			}
		}
	} 

	/**
	 * Creates and returns a deep copy of this CT. Variables are not deep copied
	 * but the reference are used.
	 * 
	 * @return A deep copy of this CT.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public CliqueTree clone() {
		CliqueTree copy = new CliqueTree();

		// copies nodes
		for (AbstractNode node : _nodes) {
			copy.addNode((LinkedHashSet<Variable>) ((CliqueNode) node)
					.getVariables().clone(), node.getName());
		}

		// copies edges
		for (Edge edge : _edges) {
			copy.addEdge(copy.getNode(edge.getHead().getName()), copy
					.getNode(edge.getTail().getName()));
		}

		copy._familyCliques = new HashMap<Variable, CliqueNode>();
		for (Variable var : _familyCliques.keySet()) {
			copy._familyCliques.put(var, (CliqueNode) copy
					.getNode(getFamilyClique(var).getName()));
		}

		if (_variableCliques != null) {
			copy._variableCliques = new HashMap<Variable, CliqueNode>();
			for (Variable var : _variableCliques.keySet()) {
				copy._variableCliques.put(var, (CliqueNode) copy
						.getNode(getVariableClique(var).getName()));
			}
		}

		if (_focusedSubtree != null) {
			copy._focusedSubtree = new LinkedHashSet<CliqueNode>();
			for (CliqueNode clique : _focusedSubtree) {
				copy._focusedSubtree.add((CliqueNode) copy.getNode(clique
						.getName()));
			}
		}

		copy._pivot = (CliqueNode) copy.getNode(_pivot.getName());

		return copy;
	}

	/**
	 * Returns the set of cliques in the minimal subtree that covers the
	 * specified collection of Variables.
	 * 
	 * @param vars
	 *            Collection of Variables whose minimal covering subtree is to
	 *            be computed.
	 * @return The set of cliques in the minimal subtree that covers the
	 *         specified collection of Variables.
	 */
	Set<CliqueNode> computeMinimalSubtree(Collection<Variable> vars) {
		// we deploy the following strategy: keep "removing" redundant leaves
		// until a minimal subtree that covers the specified collection of
		// belief node is left.
		HashMap<CliqueNode, Integer> degrees = new HashMap<CliqueNode, Integer>();
		LinkedList<CliqueNode> leaves = new LinkedList<CliqueNode>();

		// initializes degrees and leaves
		for (AbstractNode node : _nodes) {
			CliqueNode clique = (CliqueNode) node;
			int degree = clique.getDegree();

			degrees.put(clique, degree);
			if (degree == 1) {
				leaves.add(clique);
			}
		}

		// keep removing redundant leaves until list is empty
		while (!leaves.isEmpty()) {
			// pops head
			CliqueNode leaf = leaves.removeFirst();

			// finds the only alive neighbor
			CliqueNode aliveNeighbor = null;
			for (AbstractNode neighbor : leaf.getNeighbors()) {
				if (degrees.containsKey(neighbor)) {
					aliveNeighbor = (CliqueNode) neighbor;
					break;
				}
			}

			// System.out.println(" " + (aliveNeighbor==null) );
			Set<?> difference = leaf.getDifferenceTo(aliveNeighbor);
			boolean isRedudant = true;
			for (Variable var : vars) {
				if (difference.contains(var)) {
					isRedudant = false;
					break;
				}
			}

			if (isRedudant) {
				// removes leaf from degrees
				degrees.remove(leaf);

				// updates degree for alive neighbor
				int degree = degrees.get(aliveNeighbor) - 1;
				degrees.put(aliveNeighbor, degree);

				// update leaves
				if (degree == 0) {
					// only one clique left, terminate
					break;
				} else if (degree == 1) {
					leaves.add(aliveNeighbor);
				}
			}
		}

		return degrees.keySet();
	}

	/**
	 * Returns the variable clique for the specified variable.
	 * 
	 * @param var
	 *            variable whose variable clique is at request.
	 * @return The variable clique for the specified variable.
	 */
	public CliqueNode getVariableClique(Variable var) {
		return _variableCliques.get(var);
	}

	/**
	 * Returns the family covering clique for the specified variable.
	 * 
	 * @param var
	 *            variable whose family covering clique is at request.
	 * @return The family covering clique for the specified variable.
	 */
	public CliqueNode getFamilyClique(Variable var) {
		return _familyCliques.get(var);
	}

	/**
	 * Returns the pivot of this CT.
	 * 
	 * @return The pivot of this CT.
	 */
	public CliqueNode getPivot() {
		return _pivot;
	}

	/**
	 * Returns <code>true</code> if the specified clique is in the focused
	 * subtree.
	 * 
	 * @param clique
	 * @return
	 */
	public boolean inFocusedSubtree(CliqueNode clique) {
		return _focusedSubtree == null || _focusedSubtree.contains(clique);
	}

	/**
	 * The method is exclusively for HLCM natural clique trees. When use this
	 * method, one must be sure of the following points: (1) the caller clique
	 * tree must has a focusedsubtree, (2) Only msgs from a non-focused
	 * cliquenode to a focused clique node will be copied. (3) For the
	 * aforementioned edge, How to find the corresponding edge from the argument
	 * cliqueTree(<b>If any</b>)? The answer is based on the natural match
	 * between clique nodes: For a variable clique node, there is a
	 * corresponding variable clique node; for a familiy clique node, there is
	 * also a corresponding family clique node. (4) The msgs are refered rather
	 * than really copied.
	 * 
	 * @param cliqueTree
	 *            which stores messages required.
	 */
	public void copyInMsgsFrom(CliqueTree cliqueTree) {
		for (CliqueNode head : _focusedSubtree) {
			for (AbstractNode node : head.getNeighbors()) {
				if (_focusedSubtree.contains(node))
					continue;
				CliqueNode tail = (CliqueNode) node;
				Variable hVar = head.getVariables().iterator().next();
				Variable tVar = tail.getVariables().iterator().next();
				CliqueNode head_, tail_;
				if (head.getVariables().size() == 2) {
					head_ = cliqueTree.getFamilyClique(hVar);
					tail_ = cliqueTree.getVariableClique(tVar);
				} else {
					tail_ = cliqueTree.getFamilyClique(tVar);
					head_ = cliqueTree.getVariableClique(hVar);
				}
				Function message = tail_.getMessageTo(head_);
				assert message != null;
				tail.setMessageTo(head, message);

				Double normalizationDouble = tail_.getNormalizationTo(head_);
				if (normalizationDouble != null)
					tail.setNormalizationTo(head, normalizationDouble
							.doubleValue());
			}
		}
	}
	
	public HashSet<MessagesForLocalEM> copyInMsgsNodeFrom(CliqueTree cliqueTree) {
		
		HashSet<MessagesForLocalEM> set = new HashSet<MessagesForLocalEM>();
		
		for (CliqueNode head : _focusedSubtree) {
			for (AbstractNode node : head.getNeighbors()) {
				if (_focusedSubtree.contains(node))
					continue;
				CliqueNode tail = (CliqueNode) node;
				Variable hVar = head.getVariables().iterator().next();
				Variable tVar = tail.getVariables().iterator().next();
				CliqueNode head_, tail_;
				if (head.getVariables().size() == 2) {
					head_ = cliqueTree.getFamilyClique(hVar);
					tail_ = cliqueTree.getVariableClique(tVar);
				} else {
					tail_ = cliqueTree.getFamilyClique(tVar);
					head_ = cliqueTree.getVariableClique(hVar);
				}
				Function message = tail_.getMessageTo(head_);
				assert message != null;
				tail.setMessageTo(head, message);

				Double normalizationDouble = tail_.getNormalizationTo(head_);
				if (normalizationDouble != null)
					tail.setNormalizationTo(head, normalizationDouble
							.doubleValue());
				
				MessagesForLocalEM mess = new MessagesForLocalEM(head.getName(),tail.getName(),message, normalizationDouble);
				set.add(mess);
			}
		}
		
		return set;
	}
	
	public void copyInMsgsFrom(Set<MessagesForLocalEM> msgs) {
		
		
		for (MessagesForLocalEM message : msgs)
		{
			for (CliqueNode head : _focusedSubtree) 
			{
				if(head.getName().equals(message.getHead()))
				{
					for (AbstractNode node : head.getNeighbors())
					{
						if(node.getName().equals(message.getTail()))
						{
							((CliqueNode) node).setMessageTo(head, message.getFunction());

							if (message.getNormalization() != null)
								((CliqueNode) node).setNormalizationTo((CliqueNode)head, message.getNormalization().doubleValue());
						}
					}
				}else
				{
					continue;
				}
			}
		}
	}

	/**
	 * Prune this clique tree such that only clique nodes within focused area
	 * and those border on the focused area are left.
	 * 
	 */
	public void prune() {
		// Compute all nodes that should be left.
		Set<CliqueNode> nodesLeft = new LinkedHashSet<CliqueNode>();
		nodesLeft.addAll(_focusedSubtree);
		for (CliqueNode node : _focusedSubtree) {
			for (AbstractNode neighbor : node.getNeighbors()) {
				nodesLeft.add((CliqueNode) neighbor);
			}
		}

		// Eliminate all pruned nodes
		Set<CliqueNode> nodesPruned = new LinkedHashSet<CliqueNode>();
		for (AbstractNode node : getNodes()) {
			if (!nodesLeft.contains(node))
				nodesPruned.add((CliqueNode) node);
		}
		for (AbstractNode node : nodesPruned) {
			removeNode(node);
		}

		// Remove already-pruned things from _variableCliques
		Set<Variable> vars1 = new LinkedHashSet<Variable>();
		if (_variableCliques != null) {
			for (Variable var : _variableCliques.keySet()) {
				if (!nodesLeft.contains(getVariableClique(var)))
					vars1.add(var);
			}
		}
		for (Variable var : vars1)
			_variableCliques.remove(var);

		// Remove already-pruned things from _familyCliques
		Set<Variable> vars2 = new LinkedHashSet<Variable>();
		for (Variable var : _familyCliques.keySet()) {
			if (!nodesLeft.contains(getFamilyClique(var)))
				vars2.add(var);
		}
		for (Variable var : vars2)
			_familyCliques.remove(var);

	}

	/**
	 * Find the familiy cliquenode of the argument variable. Copy(Refer) the
	 * attached functions.
	 * 
	 * @param cliqueTree
	 * @param var
	 */
	public void copyFuncsFrom(CliqueTree cliqueTree, Variable var) {
		CliqueNode cNode = cliqueTree.getFamilyClique(var);
		_familyCliques.get(var).setFunctions(cNode.getFunctions());
	}
}


