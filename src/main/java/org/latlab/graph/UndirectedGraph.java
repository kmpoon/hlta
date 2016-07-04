/**
 * UndirectedGraph.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.LinkedList;
import java.util.Map;

/**
 * This class provides an implementation for undirected graphs (UGs).
 * 
 * @author Yi Wang
 * 
 */
public class UndirectedGraph extends AbstractGraph {

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
	@Override
	public Edge addEdge(AbstractNode head, AbstractNode tail) {
		// this graph must contain both nodes
		assert containsNode(head) && containsNode(tail);

		// nodes must be distinct; otherwise, self loop will be introduced.
		assert head != tail;

		// nodes cannot be neighbors; otherwise, duplicated edge will be
		// introduced.
		assert !head.hasNeighbor(tail);

		// creates edge
		Edge edge = new Edge(head, tail);

		// adds edge to the list of edges in this graph
		_edges.add(edge);

		// attachs edge to both ends
		head.attachEdge(edge);
		tail.attachEdge(edge);

		return edge;
	}

	/**
	 * Adds a node with the specified name to this graph and returns the node.
	 * 
	 * @param name
	 *            name of the node.
	 * @return the node that was added to this graph.
	 */
	@Override
	public UndirectedNode addNode(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		// name must be unique in this graph
		assert !containsNode(name);

		// creates node
		UndirectedNode node = new UndirectedNode(this, name);

		// adds node to the list of nodes in this graph
		_nodes.add(node);

		// maps name to node
		_names.put(name, node);

		return node;
	}

	/**
	 * Creates and returns a deep copy of this graph. This implementation copies
	 * everything in this graph. Consequently, it is safe to do anything you
	 * want to the deep copy.
	 * 
	 * @return a deep copy of this graph.
	 */
	@Override
	public UndirectedGraph clone() {
		UndirectedGraph copy = new UndirectedGraph();

		// copies nodes
		for (AbstractNode node : _nodes) {
			copy.addNode(node.getName());
		}

		// copies edges
		for (Edge edge : _edges) {
			copy.addEdge(copy.getNode(edge.getHead().getName()), copy
					.getNode(edge.getTail().getName()));
		}

		return copy;
	}

	/**
	 * Traverses this graph in a depth first manner. This implementation
	 * discovers the specified node and then recursively explores its unvisited
	 * neighbors.
	 * 
	 * @param node
	 *            node to start with.
	 * @param d
	 *            map from nodes to their discovering time.
	 * @param f
	 *            map from nodes to their finishing time.
	 * @return the elapsed time.
	 */
	@Override
	public final int depthFirstSearch(AbstractNode node, int time,
			Map<AbstractNode, Integer> d, Map<AbstractNode, Integer> f) {
		// this graph must contain node
		assert containsNode(node);

		// discovers node
		d.put(node, time++);

		// explores unvisited neighbors
		for (AbstractNode neighbor : node.getNeighbors()) {
			if (!d.containsKey(neighbor)) {
				time = depthFirstSearch(neighbor, time, d, f);
			}
		}

		// finishes node
		f.put(node, time++);

		return time;
	}

	/**
	 * Eliminates the specified node from this graph. To eliminate a node from a
	 * UG is to marry its broken neighbors and remove it from the graph.
	 * 
	 * @param node
	 *            node to be eliminated.
	 */
	public final void eliminateNode(AbstractNode node) {
		// this graph must contain node
		assert containsNode(node);

		// marries broken neighbors
		for (AbstractNode neighbor1 : node.getNeighbors()) {
			for (AbstractNode neighbor2 : node.getNeighbors()) {
				if (neighbor1 != neighbor2 && !neighbor1.hasNeighbor(neighbor2)) {
					addEdge(neighbor1, neighbor2);
				}
			}
		}

		// removes node
		removeNode(node);
	}

	/**
	 * Returns the names of the nodes in this graph in a minimum deficiency
	 * order.
	 * 
	 * @return the names of the nodes in this graph in a minimum deficiency
	 *         order.
	 */
	public final LinkedList<String> minimumDeficiencySearch() {
		// we use LinkedList for fast iteration
		LinkedList<String> order = new LinkedList<String>();

		// works on a deep copy of this graph
		UndirectedGraph copy = clone();

		// successively eliminates node with minimum deficiency
		while (copy.getNumberOfNodes() > 0) {
			int minDef = Integer.MAX_VALUE;
			AbstractNode elimNode = null;

			for (AbstractNode node : copy._nodes) {
				int deficiency = ((UndirectedNode) node).computeDeficiency();

				if (deficiency < minDef) {
					minDef = deficiency;
					elimNode = node;
				}
			}

			// eliminates selected node
			copy.eliminateNode(elimNode);

			// appends node name to name list
			order.add(elimNode.getName());
		}

		return order;
	}

	/**
	 * Removes the specified edge from this graph.
	 * 
	 * @param edge
	 *            edge to be removed from this graph.
	 */
	@Override
	public final void removeEdge(Edge edge) {
		// this graph must contains the edge
		assert containsEdge(edge);

		// removes edge from the list of edges in this graph
		_edges.remove(edge);

		// detachs edge from both ends
		edge.getHead().detachEdge(edge);
		edge.getTail().detachEdge(edge);
	}

	/**
	 * Returns a string representation of this graph. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this graph.
	 */
	@Override
	public String toString(int amount) {
		// amount cannot be negative
		assert amount >= 0;

		// prepares white space for indent
		StringBuffer whiteSpace = new StringBuffer();
		for (int i = 0; i < amount; i++) {
			whiteSpace.append('\t');
		}

		// builds string representation
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(whiteSpace);
		stringBuffer.append("undirected graph {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer
				.append("\tnumber of nodes = " + getNumberOfNodes() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tnodes = {\n");

		for (AbstractNode node : _nodes) {
			stringBuffer.append(node.toString(amount + 2));
		}

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\t};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer
				.append("\tnumber of edges = " + getNumberOfEdges() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tedges = {\n");

		for (Edge edge : _edges) {
			stringBuffer.append(edge.toString(amount + 2));
		}

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\t};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

}
