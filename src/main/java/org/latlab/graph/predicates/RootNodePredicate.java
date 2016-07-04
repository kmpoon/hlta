package org.latlab.graph.predicates;

import org.latlab.graph.DirectedNode;
import org.latlab.util.Predicate;

/**
 * Checks whether a node is a root node, which has no parents.
 * @author leonard
 *
 */
public class RootNodePredicate implements Predicate<DirectedNode> {

	public boolean evaluate(DirectedNode node) {
		return node.getParents().size() == 0;
	}

}
