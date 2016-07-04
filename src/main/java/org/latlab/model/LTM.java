package org.latlab.model;

import org.latlab.util.DataSet;
import org.latlab.util.Variable;
import org.latlab.util.Function;
import org.latlab.util.Function1D;
import org.latlab.util.Function2D;
import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * This class provides an implementation for HLC models.
 * 
 * @author Tao Chen
 * 
 */
public class LTM extends BayesNet {

	/**
	 * the prefix of default names of HLCMs.
	 */
	private final static String NAME_PREFIX = "HLCM";

	/**
	 * the number of created HLCMs.
	 */
	private static int _count = 0;

	/**
	 * Store the unitIncrease value.
	 */
	private double _improvement;

	/**
	 * Constructs an empty HLCM with the specified name.
	 * 
	 * @param name
	 *            name of this BN.
	 */
	public LTM(String name) {
		super(name);
		_count++;
	}

	/**
	 * Default constructor.
	 */
	public LTM() {
		this(NAME_PREFIX + _count);
	}

	/**
	 * TODO This method override BayesNet.clone(), however, they are quite
	 * similar. Creates and returns a deep copy of this HLCM. This
	 * implementation copies everything in this HLCM but the name and variables.
	 * The default name will be used for the copy instead of the original one.
	 * The variables will be reused other than deeply copied. This will
	 * facilitate learning process. However, one cannot change node names after
	 * clone. TODO avoid redundant operations on CPTs.
	 * <p>
	 * Also note that cpts are also cloned.
	 * </p>
	 * 
	 * @return a deep copy of this HLCM.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public LTM clone() {
		LTM copy = new LTM();

		// copies nodes
		for (AbstractNode node : _nodes) {
			copy.addNode(((BeliefNode) node).getVariable());
		}

		// copies edges
		for (Edge edge : _edges) {
			copy.addEdge(copy.getNode(edge.getHead().getName()), copy
					.getNode(edge.getTail().getName()));
		}
		// copies CPTs
		for (AbstractNode node : copy._nodes) {
			BeliefNode bNode = (BeliefNode) node;
			bNode.setCpt(getNode(bNode.getVariable()).getCpt().clone());
		}

		// copies loglikelihoods
		copy._loglikelihoods = (HashMap<DataSet, Double>) _loglikelihoods
				.clone();
		
		copy._modelModified = _modelModified;

		return copy;
	}

	/**
	 * Create an LCM from maniVars. The parameters are randomly set.
	 * 
	 * @param maniVars
	 *            manifest variables
	 * @param cardinality
	 *            The cardinality of the latent variable
	 * @return The resulting LCM.
	 */
	public static LTM createLCM(ArrayList<Variable> maniVars, int cardinality) {

		LTM lCM = new LTM();
		BeliefNode root = lCM.addNode(new Variable(cardinality));
		for (Variable var : maniVars) {
			lCM.addEdge(lCM.addNode(var), root);
		}
		lCM.randomlyParameterize();

		return lCM;
	}

	public static LTM createLCM(Variable[] maniVars, int cardinality) {

		LTM lCM = new LTM();
		BeliefNode root = lCM.addNode(new Variable(cardinality));
		for (Variable var : maniVars) {
			lCM.addEdge(lCM.addNode(var), root);
		}
		lCM.randomlyParameterize();

		return lCM;
	}
	/**
	 * When use this method, we suppose that there is a non-leave(latent)
	 * BeliefeNode which is of Variable var.
	 * <p>
	 * An equivalent HLCM is returned which is rooted at that node. The original
	 * HLCM is immutable and the output HLCM is a clone.
	 * </p>
	 * 
	 * @param var
	 * @return An HLCM after root walking.
	 */
	public LTM changeRoot(Variable var) {

		LTM equiModel = clone();
		BeliefNode newRoot = equiModel.getNode(var);

		assert newRoot != null && !newRoot.isLeaf();

		// collect all (copies) of latent nodes from
		// newRootInModel to root (excluding root) into Stack
		Stack<BeliefNode> path = equiModel.path2Root(newRoot);
		path.pop();

		while (!path.empty()) {
			BeliefNode nextRoot = path.pop();
			equiModel.shiftRoot(nextRoot);
		}

		return equiModel;
	}

	/**
	 * Replace the latVar by the newVar. Parameters concerning the new node are
	 * not properly defined.
	 * 
	 * @param latVar
	 * @param newVar
	 * @return
	 */
	public LTM manipulation_VariableReplacement(Variable latVar, Variable newVar) {
		LTM template = clone();
		ArrayList<BeliefNode> mutableNodesInTemplate = new ArrayList<BeliefNode>();

		BeliefNode latNode = template.getNode(latVar);
		BeliefNode newNode = template.addNode(newVar);

		Set<DirectedNode> children = latNode.getChildren();
		BeliefNode parent = (BeliefNode) latNode.getParent();
		for (DirectedNode child : children) {
			BeliefNode beliefChild = (BeliefNode) child;
			template.addEdge(beliefChild, newNode);

			mutableNodesInTemplate.add((BeliefNode) child);
		}

		if (parent != null) {
			template.addEdge(newNode, parent);

			mutableNodesInTemplate.add(newNode);
		}
		template.removeNode(latNode);

		template.randomlyParameterize(mutableNodesInTemplate);

		return template;
	}

	/**
	 * ******************* Trustable Comments *******************
	 * 
	 * v1 and v2 are two neighbors of latVar. We introduce a newNode with
	 * Variable newVar for v1 and v2. newVar has the same cardinality as latVar.
	 * Old Parameters keep the same and new parameters are set randomaly.
	 * 
	 * @param latVar
	 * @param v1
	 * @param v2
	 * @param newVar
	 * @return
	 */
	public LTM manipulation_NodeIntroduction(Variable latVar, Variable v1,
			Variable v2, Variable newVar) {
		assert getNode(latVar) != null && getNode(v1) != null
				&& getNode(v2) != null;

		LTM template = clone();
		ArrayList<BeliefNode> mutableNodesInTemplate = new ArrayList<BeliefNode>();

		// We operate in template.
		BeliefNode latNode = template.getNode(latVar);
		BeliefNode neighbor1 = template.getNode(v1);
		BeliefNode neighbor2 = template.getNode(v2);
		BeliefNode newNode = template.addNode(newVar);
		if (neighbor1.getParent() == latNode
				&& neighbor2.getParent() == latNode) {
			// Function cpt1 = neighbor1.getCpt();
			// Function cpt2 = neighbor2.getCpt();

			template.removeEdge(latNode.getEdge(neighbor1));
			template.removeEdge(latNode.getEdge(neighbor2));
			template.addEdge(newNode, latNode);
			template.addEdge(neighbor1, newNode);
			template.addEdge(neighbor2, newNode);

			mutableNodesInTemplate.add(neighbor1);
			mutableNodesInTemplate.add(neighbor2);
			mutableNodesInTemplate.add(newNode);
			// neighbor1.setCpt(cpt1.replaceVar(latVar, newVar));
			// neighbor2.setCpt(cpt2.replaceVar(latVar, newVar));
			// newNode
			// .setCpt(Function
			// .createDeterCondDistribution(latVar, newVar));
		} else {
			BeliefNode parNeighbor = latNode.getParent() == neighbor1 ? neighbor1
					: neighbor2;
			BeliefNode chiNeighbor = latNode.getParent() == neighbor1 ? neighbor2
					: neighbor1;
			// Function cpt1 = latNode.getCpt();
			// Function cpt2 = chiNeighbor.getCpt();

			template.removeEdge(latNode.getEdge(parNeighbor));
			template.removeEdge(latNode.getEdge(chiNeighbor));
			template.addEdge(newNode, parNeighbor);
			template.addEdge(latNode, newNode);
			template.addEdge(chiNeighbor, newNode);

			mutableNodesInTemplate.add(latNode);
			mutableNodesInTemplate.add(chiNeighbor);
			mutableNodesInTemplate.add(newNode);
			// newNode.setCpt(cpt1.replaceVar(latVar, newVar));
			// chiNeighbor.setCpt(cpt2.replaceVar(latVar, newVar));
			// latNode
			// .setCpt(Function
			// .createDeterCondDistribution(latVar, newVar));
		}
		template.randomlyParameterize(mutableNodesInTemplate);

		return template;

	}

	/**
	 * Current HLCM never change.
	 * 
	 * @return A cloned HLCM in which the corresponding child node has been
	 *         deleted.
	 */
	public LTM manipulation_NodeDeletion(Variable v2Delete, Variable dockVar) {
		LTM candModel = clone();
		// Operations take place in the cloned candModel
		BeliefNode n2Delete = candModel.getNode(v2Delete);
		BeliefNode dockNode = candModel.getNode(dockVar);

		assert dockNode.hasNeighbor(n2Delete);

		Set<DirectedNode> children = new HashSet<DirectedNode>();
		for (DirectedNode child : n2Delete.getChildren())
			children.add(child);

		DirectedNode parent = n2Delete.getParent();

		candModel.removeNode(n2Delete);
		for (DirectedNode child : children) {
			if (child != dockNode)
				candModel.addEdge(child, dockNode);
		}

		if (parent != null && parent != dockNode)
			candModel.addEdge(dockNode, parent);

		return candModel;
	}

	/**
	 * Relocate the node of alterVar to be child of the root. Current HLCM never
	 * change. We clone a new HLCM and do the change. Parameters are inherited
	 * except P(alterVar | root).
	 * <p>
	 * When calling this method, make sure that neither altervar is the root
	 * variable, nor the child of the root.
	 * </p>
	 * 
	 * @param alterVar
	 * @return
	 */
	public LTM manipulation_NodeRelocation(Variable alterVar) {
		LTM candModel = clone();

		ArrayList<BeliefNode> mutableNodes = new ArrayList<BeliefNode>();

		BeliefNode cAlterNode = candModel.getNode(alterVar);
		BeliefNode cParentNode = (BeliefNode) cAlterNode.getParent();
		BeliefNode cRoot = candModel.getRoot();

		candModel.removeEdge(cAlterNode.getEdge(cParentNode));
		candModel.addEdge(cAlterNode, cRoot);

		mutableNodes.add(cAlterNode);
		candModel.randomlyParameterize(mutableNodes);

		return candModel;
	}

	/**
	 * Return the set of hidden Variables. Note that the reference to these
	 * variable are contained in the Set.
	 * 
	 * @return The set of hidden Variables.
	 */
	public Set<Variable> getLatVars() {
		Set<Variable> latVars = new HashSet<Variable>();
		for (Variable var : getVariables()) {
			if (!getNode(var).isLeaf())
				latVars.add(var);
		}
		return latVars;
	}
	
	public ArrayList<Variable> getLatVarsfromTop(){
		ArrayList<Variable> LatVarsOrdered = new ArrayList<Variable>();
      //Since queue is a interface
        Queue<Variable> queue = new LinkedList<Variable>();
         //Adds to end of queue
        queue.add(getRoot().getVariable());

        while(!queue.isEmpty())
        {
            //removes from front of queue
            Variable r = queue.remove(); 
			LatVarsOrdered.add(r);
            for(Variable n: getNode(r).getChildrenLatVars()){
            	queue.add(n);
            }
        }
        
		
	    return LatVarsOrdered;
		
	}
	
	
	

	

	/**
	 * Return the set of manifest Variables. Note that the reference to these
	 * variable are contained in the Set.
	 * 
	 * @return The set of manifest(leaf) Variables.
	 */
	public Set<Variable> getManifestVars() {
		Set<Variable> manifestVars = new HashSet<Variable>();
		for (Variable var : getVariables()) {
			if (getNode(var).isLeaf())
				manifestVars.add(var);
		}
		return manifestVars;
	}

	/**
	 * Whether the model satisfies the regularity of cardinality.
	 * 
	 * @return
	 */
	public boolean isCardRegular() {
		boolean regular = true;
		for (AbstractNode node : getNodes()) {
			BeliefNode bNode = (BeliefNode) node;
			if (bNode.getVariable().getCardinality() > bNode
					.computeMaxPossibleCardInHLCM()) {
				regular = false;
				break;
			}
		}
		return regular;
	}

	public boolean isCardRegular(Variable var) {

		assert getNode(var) != null;
		BeliefNode node = getNode(var);
		if (node.isLeaf())
			return true;

		int maxPossibleCard = node.computeMaxPossibleCardInHLCM();
		int cardinality = var.getCardinality();
		if (cardinality > maxPossibleCard)
			return false;
		else
			return true;
	}

	/**
	 * Note that the collection of vars may contain manifest variables. For
	 * manifest variable, no need to check the regularity.
	 * 
	 * @param vars
	 * @return
	 */
	public boolean isCardRegular(Collection<Variable> vars) {
		boolean regular = true;
		for (Variable var : vars) {
			if (!isCardRegular(var)) {
				regular = false;
				break;
			}
		}
		return regular;
	}

	/**
	 * Get root node of this HLCM. Return null for an empty HLCM.
	 * 
	 * @return Root node.
	 */
	public BeliefNode getRoot() {

		LinkedList<AbstractNode> allNodes = getNodes();
		// return null for an empty HLCM.
		if (allNodes.isEmpty())
			return null;

		DirectedNode oneNode = (DirectedNode) allNodes.getFirst();
		while (true) {
			if (oneNode.isRoot())
				break;
			oneNode = oneNode.getParent();
		}
		return (BeliefNode) oneNode;
	}

	/**
	 * 
	 * @return unitIncrease
	 */
	public double getImprovement() {
		return _improvement;
	}

	/**
	 * Set unitIncrease.
	 */
	public void setImprovement(double improvement) {
		_improvement = improvement;
	}

	/**
	 * Suppose that child1 and child2 are children of the root node. Return a
	 * clone of this model. In the cloned model, there are corresponding child1
	 * and child2. All operations take place in the cloned model: A latent node
	 * is introduced as the new parent of child1 and child2. This new latent
	 * node is a new child of the root. The new latent node is of cardinality 2.
	 * <p>
	 * Parameters are inherited except P(child1|newly introduced Node ),
	 * P(child2|newly introduced Node ), P(newly introduced Node | root),
	 * </p>
	 * 
	 * @param child1
	 *            A child node of the root
	 * @param child2
	 *            A child node of the root
	 * @return A cloned model in which a new latent node is introduced as parent
	 *         of child1 and child2.
	 */
	public LTM introduceParent4Siblings(BeliefNode child1, BeliefNode child2) {

		assert this.containsNode(child1);
		assert this.containsNode(child2);
		assert child1.getParent().isRoot();
		assert child2.getParent().isRoot();

		LTM candModel = this.clone();
		child1 = candModel.getNode(child1.getVariable());
		child2 = candModel.getNode(child2.getVariable());
		BeliefNode root = candModel.getRoot();

		candModel.removeEdge(child1.getEdge(root));
		candModel.removeEdge(child2.getEdge(root));
		BeliefNode newNode = candModel.addNode(new Variable(2));

		candModel.addEdge(child1, newNode);
		child1.randomlyParameterize();

		candModel.addEdge(child2, newNode);
		child2.randomlyParameterize();

		candModel.addEdge(newNode, root);
		newNode.randomlyParameterize();

		return candModel;
	}

	/**
	 * Return a clone of this model. The calling model has no change. For the
	 * returned model, all thing are the same except that the root node is
	 * replaced by another one which has one more state than the original root.
	 * <p>
	 * Paramters are inherited except P(newRoot), P(Child|newRoot) for every
	 * child. These parameters are assigned but We suppose they are
	 * insignificant, that is, never been use in the future.
	 * </p>
	 * 
	 * @return A cloned HLCM in which the root canotains one more state
	 */
	public LTM introduceState4Root() {

		LTM candModel = this.clone();

		BeliefNode rootNode = candModel.getRoot();
		Variable rootVar = rootNode.getVariable();
		Set<DirectedNode> children = rootNode.getChildren();

		BeliefNode newRootNode = candModel.addNode(new Variable(rootVar
				.getCardinality() + 1));
		candModel.removeNode(rootNode);

		for (DirectedNode child : children) {
			BeliefNode beliefChild = (BeliefNode) child;

			candModel.addEdge(beliefChild, newRootNode);
			// Be careful. ctp need work.
			// But now I find no need
			// beliefChild.randomlyParameterize();
		}
		// Be careful. ctp need work.
		// But now I find no need
		// newRootNode.randomlyParameterize();

		return candModel;

	}

	/**
	 * v1->X->v2, or v1<-X<-v2, or v1<-X->v2.
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public boolean twoEdgeNeighbors(Variable var1, Variable var2) {

		assert getNode(var1) != null && getNode(var2) != null;
		assert var1 != var2;

		BeliefNode n1 = getNode(var1);
		BeliefNode n2 = getNode(var2);
		DirectedNode n1Parent = n1.getParent();
		DirectedNode n2Parent = n2.getParent();
		if (n1Parent != null) {
			if (n1Parent.getParent() == n2)
				return true;
		}
		if (n2Parent != null) {
			if (n2Parent.getParent() == n1)
				return true;
		}
		if (n1Parent != null && n2Parent != null) {
			if (n1Parent == n2Parent)
				return true;
		}

		return false;
	}

	/**
	 * Return the nodes along the path from var1 to var2 in the order.
	 * 
	 * @param var1
	 * @param var2
	 * @return
	 */
	public ArrayList<BeliefNode> computePath(Variable var1, Variable var2) {

		assert getNode(var1) != null && getNode(var2) != null;

		Stack<BeliefNode> NodeOne2Root = path2Root(getNode(var1));
		Stack<BeliefNode> NodeTwo2Root = path2Root(getNode(var2));

		BeliefNode sameNode = null;
		while (!NodeOne2Root.empty() && !NodeTwo2Root.empty()) {
			BeliefNode node1 = NodeOne2Root.pop();
			BeliefNode node2 = NodeTwo2Root.pop();
			if (node1 == node2) {
				sameNode = node1;
			} else {
				NodeOne2Root.push(node1);
				NodeTwo2Root.push(node2);
				break;
			}
		}

		ArrayList<BeliefNode> path = new ArrayList<BeliefNode>();
		while (!NodeOne2Root.empty()) {
			path.add(NodeOne2Root.pop());
		}

		Collections.reverse(path);

		path.add(sameNode);

		while (!NodeTwo2Root.empty()) {
			path.add(NodeTwo2Root.pop());
		}

		return path;
	}

	/**
	 * Compute the path from this node to Root. All BeliefNode along this path
	 * will be returned. The root is also included.
	 * 
	 * @param node
	 * @return All BeliefNodes from the node to Root.
	 */
	public Stack<BeliefNode> path2Root(BeliefNode node) {

		assert this.containsNode(node);

		Stack<BeliefNode> path = new Stack<BeliefNode>();
		path.add(node);
		while (!node.isRoot()) {
			node = (BeliefNode) node.getParent();
			path.push(node);
		}
		return path;

	}

	/**
	 * When use this method, make sure that childOfRoot is one BeliefNode in
	 * this model. Moreover, it must be a latent child of the root.
	 * <p>
	 * The method shift root to childOfRoot, i.e. only shift one step.
	 * </p>
	 * 
	 * @param childOfRoot
	 *            A latent child of the root
	 */
	private void shiftRoot(BeliefNode childOfRoot) {

		BeliefNode curRoot = (BeliefNode) childOfRoot.getParent();

		Function joint = curRoot.getCpt().times(childOfRoot.getCpt());

		Function newPr = joint.marginalize(childOfRoot.getVariable());

		// test to see if P(c)=ZERO for some state of c
		// Nevin's hlcm has this test. There ZERO is a very small value.
		// if (newPr.min() < ZERO)
		// return false;

		joint.normalize(curRoot.getVariable());
		Function newPc = joint;

		// Note: One must first change structure
		removeEdge(childOfRoot.getEdge(curRoot));
		addEdge(curRoot, childOfRoot);
		// Then deal with cpts
		curRoot.setCpt(newPc);
		childOfRoot.setCpt(newPr);

	}

	/**
	 * Transforme the model from the LTM representation to the HLCM
	 * representation. The parameters information is also transformaed. Note
	 * that the score is also kept.
	 * 
	 * @return A distributed equivalent HLCM model.
	 */
//	public HLCM LTM2HLCM(double score) {
//		BeliefNode root_LTM = this.getRoot();
//		// Construct the root in the hlcm representation
//		hlcm.Variable rootVar_hlcm = new hlcm.Variable(root_LTM.getVariable()
//				.getName(), root_LTM.getVariable().getCardinality());
//		for (int i = 0; i < rootVar_hlcm.getSize(); i++) {
//			rootVar_hlcm.setStateName(i, root_LTM.getVariable().getStates()
//					.get(i));
//		}
//		hlcm.Node root_hlcm = new hlcm.Node(rootVar_hlcm);
//		// Set the parameters(1d function)
//		Function para_LTM = root_LTM.getCpt();
//		if (para_LTM != null) {
//			root_hlcm.setPriorProb(((Function1D) para_LTM)
//					.toFunction1V(rootVar_hlcm));
//		}
//
//		// construct the model recursively
//		LTM2HLCMRecursive(root_LTM, root_hlcm);
//
//		HLCM model = new HLCM(root_hlcm);
//		model.setScore(score);
//
//		return model;
//	}

	/**
	 * Construct the HLCM model from the LTM model recursively. Actually this
	 * method is designed to construct the part of model under the node_LTM. The
	 * counterpart node of node_LTM is given as node_hlcm. Also we copy the
	 * parameters information.
	 * 
	 * @param node_LTM
	 * @param node_hlcm
	 */
//	private void LTM2HLCMRecursive(BeliefNode node_LTM, hlcm.Node node_hlcm) {
//		for (DirectedNode child : node_LTM.getChildren()) {
//			BeliefNode child_LTM = (BeliefNode) child;
//			// Construct the child in the hlcm representation
//			hlcm.Variable childVar_hlcm = new hlcm.Variable(child_LTM
//					.getVariable().getName(), child_LTM.getVariable()
//					.getCardinality());
//			for (int i = 0; i < childVar_hlcm.getSize(); i++) {
//				childVar_hlcm.setStateName(i, child_LTM.getVariable()
//						.getStates().get(i));
//			}
//			// connection
//			hlcm.Node child_hlcm = new hlcm.Node(childVar_hlcm);
//			child_hlcm.makeParent(node_hlcm);
//			// Set the parameters(1d function)
//			Function para_LTM = child_LTM.getCpt();
//			if (para_LTM != null) {
//				Function2D para_LTM_2D = (Function2D) para_LTM;
//				child_hlcm.setCondProb(para_LTM_2D.toFunction2V(childVar_hlcm,
//						node_hlcm.getVar()));
//			}
//			// construct the model recursively
//			LTM2HLCMRecursive(child_LTM, child_hlcm);
//		}
//	}

	/**
	 * Synchronize the model with the vars according to the variable name. We
	 * keep the given vars unchanged and replace the variable in the model.
	 * 
	 * We suppose that the synchronization all occur on the leaf(manifest) node.
	 * 
	 * @param vars
	 */
	public void synchronize(Variable[] vars) {
		for (int i = 0; i < vars.length; i++) {
			Variable newVar = vars[i];

			BeliefNode node = (BeliefNode) getNode(newVar.getName());
			if (node != null) {
				Variable var = node.getVariable();

				_variables.remove(var);
				_variables.put(newVar, node);
				node.replaceVar(newVar);
			}
		}
	}
	
	
	/**
	 * This boolean variable is used to tell whether the model is modified during
	 * structure/cardinality adjustment.
	 * 
	 * It is used by BI code.
	 *  
	 */
	private boolean _modelModified = false;
	
	public boolean isModelModified()
	{
		return _modelModified;
	}
	
	public void setModelModified(boolean modified)
	{
		_modelModified = modified;
	}
	
}