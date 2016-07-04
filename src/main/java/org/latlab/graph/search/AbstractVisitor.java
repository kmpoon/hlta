package org.latlab.graph.search;

import java.util.Collection;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.Edge;

/**
 * An helper class for deriving {@code Visitor}.
 * @author leonard
 *
 */
public abstract class AbstractVisitor implements Visitor {
	public AbstractVisitor() {
		this(null);
	}
	
	/**
	 * Accepts an orderer for ordering the edges.
	 * @param orderer	orders the edges, and null if the original ordering
	 * 					of the edges is used.
	 */
	public AbstractVisitor(EdgeOrderer orderer) {
		this.orderer = orderer;
	}
    /**
     * Returns the original ordering.
     */
    public Collection<Edge> order(
            AbstractNode current, Collection<Edge> edges) {
        return orderer == null? edges : orderer.order(current, edges);
    }
    
    private final EdgeOrderer orderer;
}
