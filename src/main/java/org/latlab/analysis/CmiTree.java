package org.latlab.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import org.latlab.model.BayesNet;
import org.latlab.util.Variable;

/**
 * This class implements CMI trees for storing cumulative MI sequences.
 * 
 * A CMI tree is a directed tree in which each node (except the root) is
 * attached with a CMI value and each edge is attached with a variable. A path
 * starting from the root node then maps a sequence of variables to a sequence
 * of CMI values.
 * 
 * @author wangyi
 * 
 */
public class CmiTree {

	/**
	 * This class implements nodes in CMI trees.
	 * 
	 * @author wangyi
	 * 
	 */
	private class Node {

		/**
		 * Children of this node.
		 */
		private Map<Variable, Node> _children;

		/**
		 * CMI value associated with this node.
		 */
		private double _cmi;

		/**
		 * Constructor.
		 */
		private Node() {
			_children = new HashMap<Variable, Node>();
		}

		/**
		 * Adds and returns a child with the specified variable and CMI value to
		 * this node.
		 * 
		 * @param var
		 *            The variable attached to the edge to the new child.
		 * @param cmi
		 *            The CMI value of the new child.
		 * @return The new child.
		 */
		private Node addChild(Variable var, double cmi) {
			Node child = new Node();
			child._cmi = cmi;
			_children.put(var, child);
			return child;
		}

		/**
		 * Returns the child associated with the specified variable.
		 * 
		 * @param var
		 *            The variable whose associated child is to be returned.
		 * @return The child associated with the specified variable.
		 */
		private Node getChild(Variable var) {
			return _children.get(var);
		}

		/**
		 * Returns the CMI value attached to this node.
		 * 
		 * @return The CMI value attached to this node.
		 */
		private double getValue() {
			return _cmi;
		}

		/**
		 * Sets the CMI value attached to this node.
		 * 
		 * @param cmi
		 *            The new CMI value to be attached to this node.
		 */
		private void setValue(double cmi) {
			_cmi = cmi;
		}

	}

	/**
	 * The root node of this tree.
	 */
	private Node _root;

	/**
	 * Constructor.
	 */
	protected CmiTree() {
		// the root node does not contain value
		_root = new Node();
	}

	/**
	 * Returns <code>true</code> if and only if the current tree contains the
	 * whole specified path.
	 * 
	 * In this class, a path is defined as a sequence of variables starting from
	 * the root node.
	 * 
	 * @param path
	 *            The path whose existence is to be tested.
	 * @return <code>true</code> if and only if the current tree contains the
	 *         whole specified path.
	 */
	boolean contains(List<Variable> path) {
		// start from the root node
		Node current = _root;

		// traverse the path
		for (Variable var : path) {
			current = current.getChild(var);

			if (current == null) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns the sequence of CMI values attached to the nodes on the MSP of
	 * the specified path.
	 * 
	 * @param path
	 *            The path whose attached CMI values are to be returned.
	 * @return The sequence of CMI values attached to the MSP of the specified
	 *         path.
	 */
	List<Double> getMaxSubPathValues(List<Variable> path) {
		List<Double> cmiSeq = new ArrayList<Double>();

		// start from the root node
		Node current = _root;

		// traverse the path
		for (Variable var : path) {
			current = current.getChild(var);

			// expand the sequence or terminate
			if (current == null) {
				break;
			} else {
				cmiSeq.add(current.getValue());
			}
		}

		return cmiSeq;
	}

	/**
	 * Recursively parses the pre-order string representation of the subtrees
	 * rooted at the children of the specified node.
	 * 
	 * @param iterator
	 *            An iterator over a list of tokens for the pre-order string
	 *            representation.
	 * @param node
	 *            The node for which the pre-order string representation is to
	 *            be parsed.
	 * @param model
	 *            The associated Bayes net.
	 */
	private void parsePreOrderString(ListIterator<String> iterator, Node node,
			BayesNet model) {
		while (iterator.hasNext()) {
			// two cases: (1) opening parenthesis of the block for the next
			// child; and (2) closing parenthesis of the block for this node
			String token = iterator.next();
			assert token.equals("(") || token.equals(")");

			// in the 2nd case, push back the closing parenthesis and break
			if (token.equals(")")) {
				iterator.previous();
				break;
			}

			// variable attached to the edge pointing to the child
			token = iterator.next();
			Variable var = model.getNodeByName(token).getVariable();

			// delimiter
			token = iterator.next();
			assert token.equals(",");

			// CMI value attached to the child
			token = iterator.next();
			double cmi = Double.parseDouble(token);

			// constructs the child
			Node child = node.addChild(var, cmi);

			// recursively parse the string for the child
			parsePreOrderString(iterator, child, model);

			// closing parenthesis of the block for the child
			token = iterator.next();
			assert token.equals(")");
		}
	}

	/**
	 * Parses the specified pre-order string representation of the CMI tree.
	 * 
	 * @param str
	 *            The pre-order string representation of the CMI tree.
	 * @param model
	 *            The associated Bayes net.
	 */
	protected void parsePreOrderString(String str, BayesNet model) {
		// delimiters include '(', ')', and ','
		StringTokenizer tokenizer = new StringTokenizer(str, "(),", true);

		// store all tokens in a list. linked list is used for fast iteration
		List<String> tokens = new LinkedList<String>();
		while (tokenizer.hasMoreTokens()) {
			tokens.add(tokenizer.nextToken());
		}

		// recursive parsing
		parsePreOrderString(tokens.listIterator(), _root, model);
	}

	/**
	 * Set the CMI values attached to the specified path. The path will be
	 * created if it does not exist in this tree.
	 * 
	 * @param path
	 *            The path whose associated CMI values are to be updated.
	 * @param cmiSeq
	 *            The new CMI values to be attached to the specified path.
	 */
	void putPathValues(List<Variable> path, List<Double> cmiSeq) {
		assert path.size() == cmiSeq.size();

		// start from the root
		Node current = _root;

		// traverse the path
		for (int i = 0; i < path.size(); i++) {
			Variable var = path.get(i);
			double cmi = cmiSeq.get(i);

			Node child = current.getChild(var);

			if (child == null) {
				// create a new child with the CMI value
				child = current.addChild(var, cmi);
			} else {
				// update the child's CMI value
				child.setValue(cmi);
			}

			current = child;
		}
	}

	/**
	 * Returns a pre-order string representation of this CMI tree.
	 * 
	 * @return A pre-order string representation of this CMI tree.
	 */
	protected String toPreOrderString() {
		StringBuffer str = new StringBuffer();

		// recursive construction
		toPreOrderString(str, _root);

		return str.toString();
	}

	/**
	 * Recursively constructs the pre-order string representation for the
	 * subtrees rooted at the children of the specified node.
	 * 
	 * @param str
	 *            The string to work with.
	 * @param node
	 *            The node for which the pre-order string representation is to
	 *            be constructed.
	 */
	private void toPreOrderString(StringBuffer str, Node node) {
		// consider the children one by one
		for (Entry<Variable, Node> entry : node._children.entrySet()) {
			// opening parenthesis for the block of the child
			str.append("(");

			// variable attached to the edge pointing to the child
			str.append(entry.getKey().getName());

			// delimiter
			str.append(",");

			// CMI value attached to the child
			Node child = entry.getValue();
			str.append(child.getValue());

			// recursive call for the child
			toPreOrderString(str, child);

			// closing parenthesis for the block of the child
			str.append(")");
		}
	}

}
