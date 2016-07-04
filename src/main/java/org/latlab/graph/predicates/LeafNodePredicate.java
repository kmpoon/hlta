package org.latlab.graph.predicates;

import org.latlab.graph.DirectedNode;
import org.latlab.util.Predicate;

/**
 * Checks whether a directed node is a leaf node, which has no children.
 * @author leonard
 *
 */
public class LeafNodePredicate implements Predicate<DirectedNode> {

	public boolean evaluate(DirectedNode node) {
		return node.getChildren().size() == 0;
	}

}
