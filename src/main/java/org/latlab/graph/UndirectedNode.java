/**
 * UndirectedNode.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

import java.util.Collection;

/**
 * This class provides an implementation for nodes in UGs.
 * 
 * @author Yi Wang
 * 
 */
public class UndirectedNode extends AbstractNode {

	/**
	 * <p>
	 * Constructs a node with the specified name and the specified graph to
	 * contain it.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Besides constructors of subclasses, only
	 * <code>UndirectedGraph.addNode(String)</code> is supposed to call this
	 * method. </b>
	 * </p>
	 * 
	 * @param graph
	 *            graph to contain this node.
	 * @param name
	 *            name of this node.
	 * @see UndirectedGraph#addNode(String)
	 */
	protected UndirectedNode(AbstractGraph graph, String name) {
		super(graph, name);
	}

	/**
	 * Returns the deficiency of this node. The deficiency of a node in UG is
	 * the number of broken pairs of neighbors.
	 * 
	 * @return the deficiency of this node.
	 */
	public final int computeDeficiency() {
		int deficiency = 0;

		// tests each pair of neighbors twice
		for (AbstractNode neighbor1 : getNeighbors()) {
			for (AbstractNode neighbor2 : getNeighbors()) {
				if (!neighbor1.hasNeighbor(neighbor2)) {
					deficiency++;
				}
			}
		}

		// divides by 2 due to double counting
		return deficiency / 2;
	}

	@Override
	public Collection<Edge> getAdjacentEdges() {
		return super.getEdges();
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
		stringBuffer.append("undirected node {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tname = \"" + _name + "\";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tdegree = " + getDegree() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tneighbors = { ");

		for (AbstractNode neighbor : getNeighbors()) {
			stringBuffer.append("\"" + neighbor._name + "\" ");
		}

		stringBuffer.append("};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

}
