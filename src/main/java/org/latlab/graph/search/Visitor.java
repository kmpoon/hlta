package org.latlab.graph.search;

import java.util.Collection;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.Edge;

/**
 * The depth first search uses this class to visit a node.
 * This class can be overridden to perform any extension to the
 * search.
 * @author leonard
 *
 */
public interface Visitor {
    /**
     * Called when the search discovers a node.
     * @param node  the node discovered
     * @param edge  the edge followed when discovering the node
     * @return 		whether the search should proceed for this node,
     * 				it usually indicates whether the node has not been 
     * 				discovered before
     */
    public abstract boolean discover(AbstractNode node, Edge edge);
    
    /**
     * Called when the search finishes a node
     * @param node  the node finished
     */
    public abstract void finish(AbstractNode node);
    
    /**
     * Called to give an ordering of the adjacent edges 
     * that is to be followed.
     * @param edges  adjacent edges to be followed
     * @param current   current node
     * @return  ordering of the adjacent edges to be visited
     */
    public Collection<Edge> order(
            AbstractNode current, Collection<Edge> edges);
}

