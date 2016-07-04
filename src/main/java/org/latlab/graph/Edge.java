/**
 * Edge.java
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.graph;

/**
 * This class provides an implementation for edges in graphs.
 * 
 * @author Yi Wang
 * 
 */
public final class Edge {

	/**
	 * the head of this edge.
	 */
	protected AbstractNode _head;

	/**
	 * the tail of this edge.
	 */
	protected AbstractNode _tail;

	/**
	 * <p>
	 * Constructs an edge to connect the specified head and tail.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Only
	 * <code>DirectedAcyclicGraph.addEdge(AbstractNode, AbstractNode)</code> and
	 * <code>UndirectedGraph.addEdge(AbstractNode, AbstractNode)</code> are
	 * supposed to call this method. </b>
	 * </p>
	 * 
	 * @param head
	 *            head of this edge.
	 * @param tail
	 *            tail of this edge.
	 * @see DirectedAcyclicGraph#addEdge(AbstractNode, AbstractNode)
	 * @see UndirectedGraph#addEdge(AbstractNode, AbstractNode)
	 */
	public Edge(AbstractNode head, AbstractNode tail) {
		_head = head;
		_tail = tail;
	}

	/**
	 * Returns the head of this edge.
	 * 
	 * @return the head of this edge.
	 */
	public final AbstractNode getHead() {
		return _head;
	}

	/**
	 * Returns the opposite to the specified end.
	 * 
	 * @param end
	 *            end to which the opposite is at request.
	 * @return the opposite to the specified end.
	 */
	public final AbstractNode getOpposite(AbstractNode end) {
		// the argument end must be incident to this edge
		assert end == _head || end == _tail;

		return end == _head ? _tail : _head;
	}

	/**
	 * Returns the tail of this edge.
	 * 
	 * @return the tail of this edge.
	 */
	public final AbstractNode getTail() {
		return _tail;
	}

	/**
	 * Returns a string representation of this edge. This implementation returns
	 * <code>toString(0)</code>.
	 * 
	 * @return a string representation of this edge.
	 * @see #toString(int)
	 */
	@Override
	public final String toString() {
		return toString(0);
	}

	/**
	 * Returns a string representation of this edge. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this edge.
	 */
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
		stringBuffer.append("edge {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\thead = \"" + _head.getName() + "\";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\ttail = \"" + _tail.getName() + "\";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

}