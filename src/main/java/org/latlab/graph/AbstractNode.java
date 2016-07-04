/**
 * AbstractNode.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * This class provides a skeletal implementation for nodes in graphs to minimize
 * the effort required to implement such classes.
 * 
 * @author Yi Wang
 * 
 */
public abstract class AbstractNode {

	/**
	 * the graph that contains this node.
	 */
	protected AbstractGraph _graph;

	/**
	 * the name of this node.
	 */
	protected String _name;

	/**
	 * the map from neighbors of this node to incident edges. we use
	 * <code>LinkedHashMap</code> for predictable iteration order.
	 */
	protected LinkedHashMap<AbstractNode, Edge> _neighbors;

	/**
	 * <p>
	 * Constructs a node with the specified name and the specified graph to
	 * contain it.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only constructors of subclasses are supposed to call this
	 * method. </b>
	 * </p>
	 * 
	 * @param graph
	 *            graph to contain this node.
	 * @param name
	 *            name of this node.
	 */
	protected AbstractNode(AbstractGraph graph, String name) {
		_graph = graph;
		_name = name;
		_neighbors = new LinkedHashMap<AbstractNode, Edge>();
	}

	/**
	 * <p>
	 * Attachs the specified edge to this node by updating the map from
	 * neighbors to indicent edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only
	 * <code>DirectedAcyclicGraph.addEdge(AbstractNode, AbstractNode)</code> and
	 * <code>UndirectedGraph.addEdge(AbstractNode, AbstractNode)</code> are
	 * supposed to call this method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            edge to be attached to this node.
	 * @see DirectedAcyclicGraph#addEdge(AbstractNode, AbstractNode)
	 * @see UndirectedGraph#addEdge(AbstractNode, AbstractNode)
	 */
	protected void attachEdge(Edge edge) {
		// maps opposite, namely, the new neighbor, to edge
		_neighbors.put(edge.getOpposite(this), edge);
	}

	/**
	 * <p>
	 * Detachs the specified edge from this node by updating the map from
	 * neighbors to indicent edges of this node.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>DirectedAcyclicGraph.removeEdge(Edge)</code> and
	 * <code>UndirectedGraph.removeEdge(Edge)</code> are supposed to call this
	 * method. </b>
	 * </p>
	 * 
	 * @param edge
	 *            edge to be detached from this node.
	 * @see DirectedAcyclicGraph#removeEdge(Edge)
	 * @see UndirectedGraph#removeEdge(Edge)
	 */
	protected void detachEdge(Edge edge) {
		// removes opposite from the collection of neighbors of this node
		_neighbors.remove(edge.getOpposite(this));
	}

	/**
	 * <p>
	 * Disposes this node. This implementation sets the graph that contains this
	 * node to <code>null</code> such that the node can be used nowhere.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only <code>AbstractGraph.removeNode(AbstractNode)</code> is
	 * supposed to call this method. </b>
	 * </p>
	 */
	protected final void dispose() {
		_graph = null;
	}

	/**
	 * Returns the degree, namely, the number of neighbors, of this node.
	 * 
	 * @return the degree of this node.
	 */
	public final int getDegree() {
		return _neighbors.size();
	}

	/**
	 * Returns the collection of edges incident to this node. For the sake of
	 * efficiency, this implementation returns the reference that is backed by a
	 * private field. Make sure you understand this before using this method.
	 * 
	 * @return the collection of edges incident to this node.
	 */
	public final Collection<Edge> getEdges() {
		return _neighbors.values();
	}

	/**
	 * Returns a collection of all edges that lead to nodes adjacent to this
	 * node.
	 * 
	 * @return
	 */
	public abstract Collection<Edge> getAdjacentEdges();

	/**
	 * Returns the graph that contains this node.
	 * 
	 * @return the graph that contains this node.
	 */
	public final AbstractGraph getGraph() {
		return _graph;
	}

	/**
	 * Return the edge between this node and the argument node. Return null if
	 * there is no edge between them.
	 * 
	 * @author csct
	 * 
	 * @param node
	 * @return
	 */
	public Edge getEdge(AbstractNode node) {
		// two nodes must be in same graph
		assert _graph.containsNode(node);
		return _neighbors.get(node);
	}

	/**
	 * Returns the name of this node.
	 * 
	 * @return the name of this node.
	 */
	public final String getName() {
		return _name;
	}

	/**
	 * Returns the set of neighbors of this node. For the sake of efficiency,
	 * this implementation returns the reference that is backed by a private
	 * field. Make sure you understand this before using this method.
	 * 
	 * @return the set of neighbors of this node.
	 */
	public final Set<AbstractNode> getNeighbors() {
		return _neighbors.keySet();
	}

	/**
	 * Returns <code>true</code> if the specified node is a neighbor of this
	 * node.
	 * 
	 * @param node
	 *            node whose neighborship to this node is to be tested.
	 * @return <code>true</code> if the specified node is a neighbor of this
	 *         node.
	 */
	public final boolean hasNeighbor(AbstractNode node) {
		// two nodes must be in same graph
		assert _graph.containsNode(node);

		return _neighbors.containsKey(node);
	}

	/**
	 * Replaces the name of this node.
	 * 
	 * @param name
	 *            new name of this node.
	 */
	public void setName(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		// name must be unique in graph
		assert !_graph.containsNode(name);

		_graph._names.remove(_name);

		_name = name;

		_graph._names.put(_name, this);
	}

	/**
	 * Returns a string representation of this node. This implementation returns
	 * <code>toString(0)</code>.
	 * 
	 * @return a string representation of this node.
	 * @see #toString(int)
	 */
	@Override
	public String toString() {
		return toString(0);
	}

	/**
	 * Returns a string representation of this node. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this node.
	 */
	public abstract String toString(int amount);

}
