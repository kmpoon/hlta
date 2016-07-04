package org.latlab.graph.search;

import java.util.List;

import org.latlab.graph.AbstractGraph;
import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.predicates.RootNodePredicate;
import org.latlab.util.Algorithm;
import org.latlab.util.Caster;

/**
 * Performs a depth first search on a directed acyclic graph.
 * A custom operation based on the depth first search can be implemented
 * by extending the {@code Visitor} class.
 * @author leonard
 *
 */
public class DepthFirstSearch {
    /**
     * Constructor
     * @param graph a graph to search on
     */
    public DepthFirstSearch(AbstractGraph graph) {
        this.graph = graph;
    }
    
    /**
     * Performs the search.  Repeated starts the search from all root
     * nodes in the graph.
     * @param visitor	visitor for the nodes
     */
    public void perform(Visitor visitor) {
    	List<DirectedNode> roots = 
    		Algorithm.filter(graph.getNodes(), 
    				new Caster<DirectedNode>(), new RootNodePredicate());
    	
    	for (DirectedNode root : roots) {
    		perform(root, visitor);
    	}
    }
    
    /**
     * Performs the search.  The search only visits those nodes
     * connected to the start node.
     * @param start     the start node
     * @param visitor   visitor for the nodes
     */
    public void perform(AbstractNode start, Visitor visitor) {
        // this graph must contain the argument node
        assert graph.containsNode(start);
        
        if (visitor.discover(start, null)) {
	        transverseChildren(start, visitor);
	        visitor.finish(start);
        }
    }
    
    /**
     * Transverses the children of a node.
     * @param node      the parent node
     * @param visitor   visitor for the children nodes
     */
    private void transverseChildren(AbstractNode node, Visitor visitor) {
        for (Edge edge : visitor.order(node, node.getAdjacentEdges())) {
            AbstractNode adjacentNode = edge.getOpposite(node);
            
            if (visitor.discover(adjacentNode, edge)) {
                transverseChildren(adjacentNode, visitor);
                visitor.finish(adjacentNode);
            }
        }
    }
    
    private final AbstractGraph graph;
    
}
