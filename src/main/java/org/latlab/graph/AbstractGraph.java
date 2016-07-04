/**
 * AbstractGraph.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * This class provides a skeletal implementation for graphs, to minimize the
 * effort required to implement relevant classes.
 * 
 * @author Yi Wang
 * 
 */
public abstract class AbstractGraph implements Cloneable {

	/**
	 * the list of nodes in this graph. we use <code>LinkedList</code> for fast
	 * iteration.
	 */
	protected LinkedList<AbstractNode> _nodes;

	/**
	 * the list of edges in this graph. we use <code>LinkedList</code> for fast
	 * iteration.
	 */
	protected LinkedList<Edge> _edges;

	/**
	 * the map from names to nodes. it enables fast node indexing.
	 */
	protected HashMap<String, AbstractNode> _names;

	/**
	 * Constructs an empty graph.
	 */
	public AbstractGraph() {

		_nodes = new LinkedList<AbstractNode>();
		_edges = new LinkedList<Edge>();
		_names = new HashMap<String, AbstractNode>();
	}

	/**
	 * Adds an edge that connects the two specified nodes to this graph and
	 * returns the edge.
	 * 
	 * @param head
	 *            head of the edge.
	 * @param tail
	 *            tail of the edge.
	 * @return the edge that was added to this graph.
	 */
	public abstract Edge addEdge(AbstractNode head, AbstractNode tail);

	/**
	 * Adds a node with the specified name to this graph and returns the node.
	 * 
	 * @param name
	 *            name of the node.
	 * @return the node that was added to this graph.
	 */
	public abstract AbstractNode addNode(String name);

	/**
	 * Returns <code>true</code> if this graph contains the specified edge.
	 * 
	 * @param edge
	 *            edge whose presence in this graph is to be tested.
	 * @return <code>true</code> if the specified edge is present.
	 */
	public final boolean containsEdge(Edge edge) {
		return edge.getHead().getGraph() == this;
	}

	/**
	 * Returns <code>true</code> if this graph contains the specified node.
	 * 
	 * @param node
	 *            node whose presence in this graph is to be tested.
	 * @return <code>true</code> if the specified node is present.
	 */
	public final boolean containsNode(AbstractNode node) {
		return node.getGraph() == this;
	}

	/**
	 * Returns <code>true</code> if this graph contains a node with the
	 * specified name.
	 * 
	 * @param name
	 *            name whose presence in this graph is to be tested.
	 * @return <code>true</code> if this graph contains a node with the
	 *         specified name.
	 */
	public final boolean containsNode(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		return _names.containsKey(name);
	}

	/**
	 * Returns <code>true</code> if this graph contains the specified collection
	 * of nodes.
	 * 
	 * @param nodes
	 *            collectin of nodes whose presence in this graph are to be
	 *            tested.
	 * @return <code>true</code> if this graph contains the specified collection
	 *         of nodes.
	 */
	public final boolean containsNodes(Collection<? extends AbstractNode> nodes) {
		return _nodes.containsAll(nodes);
	}

	/**
	 * Returns <code>true</code> if there is a path (directed path for DAG)
	 * between the two specified nodes.
	 * 
	 * @param start
	 *            start of a path whose presence in this graph is to be tested.
	 * @param end
	 *            end of a path whose presence in this graph is to be tested.
	 * @return <code>true</code> if a path is present.
	 */
	public final boolean containsPath(AbstractNode start, AbstractNode end) {
		// this graph must contain both start and end
		assert containsNode(start) && containsNode(end);

		// discovering and finishing time
		HashMap<AbstractNode, Integer> d = new HashMap<AbstractNode, Integer>();
		HashMap<AbstractNode, Integer> f = new HashMap<AbstractNode, Integer>();

		// DFS
		depthFirstSearch(start, 0, d, f);

		// returns true if the end has been discovered
		return d.containsKey(end);
	}


	/**
	 * Traverses this graph in a depth first manner. This implementation
	 * discovers the specified node and then recursively explores its unvisited
	 * neighbors (children for DAG).
	 * 
	 * @param node
	 *            node to start with.
	 * @param time
	 *            The begining time
	 * @param d
	 *            map from nodes to their discovering time.
	 * @param f
	 *            map from nodes to their finishing time.
	 * @return the elapsed time.
	 */
	public abstract int depthFirstSearch(AbstractNode node, int time,
			Map<AbstractNode, Integer> d, Map<AbstractNode, Integer> f);

	/**
	 * Returns the list of edges in this graph. For the sake of efficiency, this
	 * implementation returns the reference to the protected field. Make sure
	 * you understand this before using this method.
	 * 
	 * @return the list of edges in this graph.
	 */
	public final LinkedList<Edge> getEdges() {
		return _edges;
	}

	/**
	 * Returns the node with the specified name in this graph.
	 * 
	 * @param name
	 *            name of the node.
	 * @return the node with the specified name in this graph; returns
	 *         <code>null</code> if none uses this name.
	 */
	public final AbstractNode getNode(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		return _names.get(name);
	}

	/**
	 * Returns the list of nodes in this graph. For the sake of efficiency, this
	 * implementation returns the reference to the protected field. Make sure
	 * you understand this before using this method.
	 * 
	 * @return the list of nodes in this graph.
	 */
	public final LinkedList<AbstractNode> getNodes() {
		return _nodes;
	}

	/**
	 * Returns the number of edges in this graph.
	 * 
	 * @return the number of edges in this graph.
	 */
	public final int getNumberOfEdges() {
		return _edges.size();
	}

	/**
	 * Returns the number of nodes in this graph.
	 * 
	 * @return the number of nodes in this graph.
	 */
	public final int getNumberOfNodes() {
		return _nodes.size();
	}

	/**
	 * Removes the specified edge from this graph. <b></b>
	 * 
	 * @param edge
	 *            edge to be removed from this graph.
	 */
	public abstract void removeEdge(Edge edge);

	/**
	 * Removes the specified node from this graph. <b>A node is removed from
	 * this graph means two things: First, this graph no longer contains this
	 * node, Second, this node has no place(graph) to live. Therefore, we need
	 * call AbstraceNode.dispose() to fulfill the second meaning.</b>
	 * 
	 * @param node
	 *            node to be removed from this graph.
	 */
	public void removeNode(AbstractNode node) {
		// this graph must contain the argument node
		assert containsNode(node);

		// removes incident edges.
		// Needed to make a copy, otherwise it modifies the
		// edges list during iteration and will throw exception
		Collection<Edge> incidentEdges = new ArrayList<Edge>(node.getEdges());
		for (Edge edge : incidentEdges) {
			removeEdge(edge);
		}

		// removes node from the list of nodes in this graph
		_nodes.remove(node);

		// removes name from the map for indexing
		_names.remove(node.getName());

		// the node is useless, dispose it.
		node.dispose();
	}

	/**
	 * Returns a string representation of this graph. This implementation
	 * returns <code>toString(0)</code>.
	 * 
	 * @return a string representation of this graph.
	 * @see #toString(int)
	 */
	@Override
	public final String toString() {
		return toString(0);
	}

	/**
	 * Returns a string representation of this graph. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this graph.
	 */
	public abstract String toString(int amount);
    
	/**
	 * Move the denoted node in _nodes to the head of the LinkedList 
	 * @param name    
	 * the String of the node you want to move
	 */
	
	public void move2First(String name){
	   AbstractNode n = _names.get(name);
	   _nodes.remove(n);
	   _nodes.addFirst(n);
	}
}