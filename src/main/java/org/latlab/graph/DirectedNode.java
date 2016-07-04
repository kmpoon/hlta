/**
 * DirectedNode.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * This class provides an implementation for nodes in DAGs.
 * 
 * @author Yi Wang
 * 
 */
public class DirectedNode extends AbstractNode {

	/**
	 * the map from parents of this node to incoming edges. we use
	 * <code>LinkedHashMap</code> for predictable iteration order.
	 */
	protected LinkedHashMap<DirectedNode, Edge> _parents;

	/**
	 * the map from children of this node to outgoing edges. we use
	 * <code>LinkedHashMap</code> for predictable iteration order.
	 */
	protected LinkedHashMap<DirectedNode, Edge> _children;

	/**
	 * <p>
	 * Constructs a node with the specified name and the specified graph to
	 * contain it.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Besides constructors of subclasses, only
	 * <code>DirectedAcyclicGraph.addNode(String)</code> is supposed to call
	 * this method. </b>
	 * </p>
	 * 
	 * @param graph
	 *            graph to contain this node.
	 * @param name
	 *            name of this node.
	 * @see DirectedAcyclicGraph#addNode(String)
	 */
	protected DirectedNode(AbstractGraph graph, String name) {
		super(graph, name);

		_parents = new LinkedHashMap<DirectedNode, Edge>();
		_children = new LinkedHashMap<DirectedNode, Edge>();
	}

	/**
	 * <p>
	 * Attachs the specified incoming edge to this node by updating the map from
	 * parents to incoming edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only
	 * <code>DirectedAcyclicGraph.addEdge(AbstractNode, AbstractNode)</code> and
	 * <code>BeliefNode.attachInEdge(Edge)</code> are supposed to call this
	 * method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            incoming edge to be attached to this node.
	 * @see DirectedAcyclicGraph#addEdge(AbstractNode, AbstractNode)
	 * @see org.latlab.model.BeliefNode#attachInEdge(Edge)
	 */
	protected void attachInEdge(Edge edge) {

		// maps tail, namely, the new parent, to edge
		_parents.put((DirectedNode) edge.getTail(), edge);
	}

	/**
	 * <p>
	 * Attachs the specified outgoing edge to this node by updating the map from
	 * children to outgoing edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only
	 * <code>DirectedAcyclicGraph.addEdge(AbstractNode, AbstractNode)</code> is
	 * supposed to call this method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            outgoing edge to be attached to this node.
	 * @see DirectedAcyclicGraph#addEdge(AbstractNode, AbstractNode)
	 */
	protected final void attachOutEdge(Edge edge) {
		// maps head, namely, the new child, to edge
		_children.put((DirectedNode) edge.getHead(), edge);
	}

	/**
	 * <p>
	 * Detachs the specified incoming edge from this node by updating the map
	 * from parents to incoming edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>DirectedAcyclicGraph.removeEdge(Edge)</code> and
	 * <code>BeliefNode.detachInEdge(Edge)</code> are supposed to call this
	 * method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            incoming edge to be detached from this node.
	 * @see DirectedAcyclicGraph#removeEdge(Edge)
	 * @see org.latlab.model.BeliefNode#detachInEdge(Edge)
	 */
	protected void detachInEdge(Edge edge) {
		// removes tail from the collection of parents of this node
		_parents.remove(edge.getTail());
	}

	/**
	 * <p>
	 * Detachs the specified outgoing edge from this node by updating the map
	 * from children to outgoing edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>DirectedAcyclicGraph.removeEdge(Edge)</code> is
	 * supposed to call this method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            outgoing edge to be detached from this node.
	 * @see DirectedAcyclicGraph#removeEdge(Edge)
	 */
	protected final void detachOutEdge(Edge edge) {
		// removes head from the collection of children of this node
		_children.remove(edge.getHead());
	}

	/**
	 * Returns the set of children of this node. For the sake of efficiency,
	 * this implementation returns the reference to a private field. Make sure
	 * you understand this before using this method.
	 * 
	 * @return the set of children of this node.
	 */
	public final Set<DirectedNode> getChildren() {
		return _children.keySet();
	}

	/**
	 * Returns a collection of the edges containing between this node and its
	 * children.
	 * 
	 * @return collection of edges to its children
	 */
	public Collection<Edge> getChildEdges() {
		return _children.values();
	}

	/**
	 * Returns the edges incident from this node.
	 */
	@Override
	public Collection<Edge> getAdjacentEdges() {
		return getChildEdges();
	}

	/**
	 * Returns the set of descendants of this node.
	 * 
	 * @return the set of descendants of this node.
	 */
	public final Set<AbstractNode> getDescendants() {
		// discovering and finishing time
		HashMap<AbstractNode, Integer> d = new HashMap<AbstractNode, Integer>();
		HashMap<AbstractNode, Integer> f = new HashMap<AbstractNode, Integer>();

		// starting with this node, DFS
		_graph.depthFirstSearch(this, 0, d, f);

		// except this node, all discovered nodes are descendants
		Set<AbstractNode> descendants = d.keySet();
		descendants.remove(this);

		return descendants;
	}

	/**
	 * Returns the incoming degree of this node.
	 * 
	 * @return the incoming degree of this node.
	 */
	public final int getInDegree() {
		return _parents.size();
	}

	/**
	 * Returns the outgoing degree of this node.
	 * 
	 * @return the outgoing degree of this node.
	 */
	public final int getOutDegree() {
		return _children.size();
	}

	/**
	 * This method returns a parent of this node. If the node is a root, return
	 * null. When we call this method for a node in an HLCM in which every
	 * non-root node has only one parent, the output is certain.
	 * 
	 * @author csct
	 * @return One Parent of this node. Null if this is a root.
	 */
	public DirectedNode getParent() {
		if (isRoot()) {
			return null;
		}

		return getParents().iterator().next();
	}

	/**
	 * Returns the set of parents of this node. For the sake of efficiency, this
	 * implementation returns the reference to a private field. Make sure you
	 * understand this before using this method.
	 * 
	 * @return the set of parents of this node.
	 */
	public final Set<DirectedNode> getParents() {
		return _parents.keySet();
	}

	/**
	 * Returns a collection of the edges incident to this node.
	 * 
	 * @return collection of edges from its parents
	 */
	public Collection<Edge> getParentEdges() {
		return _parents.values();
	}

	/**
	 * Returns <code>true</code> if the specified node is a child of this node.
	 * 
	 * @param node
	 *            node whose relationship to this node is to be tested.
	 * @return <code>true</code> if the specified node is a child of this node.
	 */
	public final boolean hasChild(AbstractNode node) {
		// two nodes must be in same graph
		assert _graph.containsNode(node);

		return _children.containsKey(node);
	}

	/**
	 * Returns <code>true</code> if the specified node is a parent of this node.
	 * 
	 * @param node
	 *            node whose relationship to this node is to be tested.
	 * @return <code>true</code> if the specified node is a parent of this node.
	 */
	public final boolean hasParent(AbstractNode node) {
		// two nodes must be in same graph
		assert _graph.containsNode(node);

		return _parents.containsKey(node);
	}

	/**
	 * Returns <code>true</code> if this node is a leaf.
	 * 
	 * @return <code>true</code> if this node is a leaf.
	 */
	public final boolean isLeaf() {
		return _children.isEmpty();
	}

	/**
	 * Returns <code>true</code> if this node is a root.
	 * 
	 * @return <code>true</code> if this node is a root.
	 */
	public final boolean isRoot() {
		return _parents.isEmpty();
	}

	/**
	 * Returns a string representation of this node. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this node.
	 */
	@Override
	public String toString(int amount) {
		// amount must be non-negative
		assert amount >= 0;

		// prepares white space for indent
		StringBuffer whiteSpace = new StringBuffer();
		for (int i = 0; i < amount; i++) {
			whiteSpace.append("\t");
		}

		// builds string representation
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(whiteSpace);
		stringBuffer.append("directed node {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tname = \"" + _name + "\";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tincoming degree = " + getInDegree() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tparents = { ");
		for (AbstractNode parent : getParents()) {
			stringBuffer.append("\"" + parent._name + "\" ");
		}
		stringBuffer.append("};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\toutgoing degree = " + getOutDegree() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tchildren = { ");

		for (AbstractNode child : getChildren()) {
			stringBuffer.append("\"" + child._name + "\" ");
		}

		stringBuffer.append("};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

}
