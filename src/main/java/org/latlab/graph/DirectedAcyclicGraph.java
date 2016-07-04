/**
 * DirectedAcyclicGraph.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * This class provides an implementation for directed acyclic graphs (DAGs).
 * 
 * @author Yi Wang
 * 
 */
public class DirectedAcyclicGraph extends AbstractGraph {
	/**
	 * Default constructor.
	 */
	public DirectedAcyclicGraph() { }
	
	/**
	 * Copy constructor.  The nodes and edges of this graph 
	 * are added according to those of the specified graph.  However, 
	 * those nodes added are not the same instances as those of the
	 * specified graph, but only the names of them are the same. 
	 * @param graph	graph to copy from
	 */
	public DirectedAcyclicGraph(DirectedAcyclicGraph graph) {
		// copies nodes
		for (AbstractNode node : graph._nodes) {
			addNode(node.getName());
		}

		// copies edges
		for (Edge edge : graph._edges) {
			addEdge(getNode(edge.getHead().getName()), 
					getNode(edge.getTail().getName()));
		}
	}

	/**
	 * Adds an edge that connects the two specified nodes to this graph and
	 * returns the edge. There is going to be a run time assert exception if the
	 * resulting graph will contain cycle.
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

		// nodes cannot be neighbors; otherwise, either duplicated edge or
		// directed cycle will be introduced.
		assert !head.hasNeighbor(tail);

		// this graph cannot contain directed path from head to tail; otherwise,
		// a directed cycle will be introduced.
		assert !containsPath(head, tail);

		// creates the edge
		Edge edge = new Edge(head, tail);

		// adds the edge to the list of edges in this graph
		_edges.add(edge);

		// attaches the edge to both ends
		head.attachEdge(edge);
		tail.attachEdge(edge);

		((DirectedNode) head).attachInEdge(edge);
		((DirectedNode) tail).attachOutEdge(edge);

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
	public DirectedNode addNode(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		// name must be unique in this graph
		assert !containsNode(name);

		// creates node
		DirectedNode node = new DirectedNode(this, name);

		// adds the node to the list of nodes in this graph
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
	public DirectedAcyclicGraph clone() {
		return (DirectedAcyclicGraph) copyTo(new DirectedAcyclicGraph());
	}

	/**
	 * Returns the moral graph of this graph. I assume that you know what is a
	 * moral graph. Otherwise, you are not supposed to use this method :-)
	 * 
	 * @return the moral graph of this graph.
	 */
	public final UndirectedGraph computeMoralGraph() {
		UndirectedGraph moralGraph = new UndirectedGraph();

		// copies nodes in this graph
		for (AbstractNode node : _nodes) {
			moralGraph.addNode(node.getName());
		}

		// copies edges in this graph with directions dropped
		for (Edge edge : _edges) {
			moralGraph.addEdge(moralGraph.getNode(edge.getHead().getName()),
					moralGraph.getNode(edge.getTail().getName()));
		}

		// connects nodes that are divorced parents of some node in this DAG.
		for (AbstractNode node : _nodes) {
			DirectedNode dNode = (DirectedNode) node;

			for (DirectedNode parent1 : dNode.getParents()) {
				AbstractNode neighbor1 = moralGraph.getNode(parent1.getName());

				for (DirectedNode parent2 : dNode.getParents()) {
					AbstractNode neighbor2 = moralGraph.getNode(parent2
							.getName());

					if (neighbor1 != neighbor2
							&& !neighbor1.hasNeighbor(neighbor2)) {
						moralGraph.addEdge(neighbor1, neighbor2);
					}
				}
			}
		}

		return moralGraph;
	}

	/**
	 * Traverses this graph in a depth first manner. This implementation
	 * discovers the specified node and then recursively explores its unvisited
	 * children.
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
		// this graph must contain the argument node
		assert containsNode(node);

		// discovers the argument node
		d.put(node, time++);

		// explores unvisited children
		for (DirectedNode child : ((DirectedNode) node).getChildren()) {
			if (!d.containsKey(child)) {
				time = depthFirstSearch(child, time, d, f);
			}
		}

		// finishes the argument node
		f.put(node, time++);

		return time;
	}

	/**
	 * Removes the specified edge from this graph.
	 * 
	 * @param edge
	 *            edge to be removed from this graph.
	 */
	@Override
	public void removeEdge(Edge edge) {
		// this graph must contain the argument edge
		assert containsEdge(edge);

		// removes edge from the list of edges in this graph
		_edges.remove(edge);

		// detachs edge from both ends
		edge.getHead().detachEdge(edge);
		edge.getTail().detachEdge(edge);

		((DirectedNode) edge.getHead()).detachInEdge(edge);
		((DirectedNode) edge.getTail()).detachOutEdge(edge);
	}

	/**
	 * Returns the nodes in this graph in a topological order.
	 * 
	 * @return the nodes in this graph in a topological order.
	 */
	public final AbstractNode[] topologicalSort() {
		// discovering and finishing time
		HashMap<AbstractNode, Integer> d = new HashMap<AbstractNode, Integer>();
		HashMap<AbstractNode, Integer> f = new HashMap<AbstractNode, Integer>();

		// DFS
		int time = 0;
		for (AbstractNode node : _nodes) {
			if (!d.containsKey(node)) {
				time = depthFirstSearch(node, time, d, f);
			}
		}

		// sorts nodes in descending order with respect to their finishing time.
		// note that the finishing time lies in [1, elasped time - 1].
		AbstractNode[] nodes = new AbstractNode[time];
		for (AbstractNode node : _nodes) {
			nodes[time - f.get(node)] = node;
		}

		// remove nulls
		AbstractNode[] compactNodes = new AbstractNode[getNumberOfNodes()];
		int i = 0;
		for (AbstractNode node : nodes) {
			if (node != null) {
				compactNodes[i++] = node;
			}
		}

		return compactNodes;
	}
	
	/**
	 * Reverses the directions of the edges and returns the resulting
	 * directed graph.
	 * @return	graph with directions of edges reversed
	 */
	public DirectedAcyclicGraph reverseEdges() {
        DirectedAcyclicGraph result = new DirectedAcyclicGraph();

        // copies nodes
        for (AbstractNode node : _nodes) {
            result.addNode(node.getName());
        }

        // copies edges
        for (Edge edge : _edges) {
            AbstractNode originalHead = 
                result.getNode(edge.getHead().getName()); 
            AbstractNode originalTail =
                result.getNode(edge.getTail().getName()); 
            
            // reverse the edge
            result.addEdge(originalTail, originalHead);
        }

        return result;
	}
	
	/**
	 * Returns an undirected graph based on this graph by ignoring 
	 * the directions of the edges.
	 * @return	undirected version of this graph
	 */
	public UndirectedGraph getUndirectedGraph() {
		return (UndirectedGraph) copyTo(new UndirectedGraph());
	}
	
	/**
	 * Copies the nodes and edges from this graph to the given graph.
	 * The nodes and edges in the copied graph are separate instances from
	 * those in this graph.
	 * @param graph	graph copied to and returned
	 * @return		graph copied to
	 */
	private AbstractGraph copyTo(AbstractGraph copy) {
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
	 * Returns a string representation of this graph. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this graph.
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
		stringBuffer.append("directed acyclic graph {\n");

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
		stringBuffer.append("};");

		return stringBuffer.toString();
	}

}