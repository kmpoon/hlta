package org.latlab.graph.search;

import java.util.Collection;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.Edge;

/**
 * Gives an ordering to those alternatives edges so that the search 
 * explores the edges following this ordering. 
 * @author leonard
 *
 */
public interface EdgeOrderer {
    public Collection<Edge> order(AbstractNode current, Collection<Edge> edges);
}
