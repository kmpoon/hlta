package org.latlab.graph.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.Edge;

/**
 * A visitor to record the discovering and finishing times
 * of the nodes during a depth first search.
 * @author leonard
 *
 */
public class TimeVisitor extends AbstractVisitor {
	
	
	private boolean reVisitTwice = false;
	
	private AbstractNode root = null;
	
	public TimeVisitor() {
		time = 0;
	}
    /**
     * Constructor
     * @param initialTime   initial time
     */
    public TimeVisitor(int initialTime) {
        time = initialTime;
    }
    
    public void setRoot(AbstractNode node)
    {
    	root = node;
    }
    
    public boolean reVisit()
    {
    	return reVisitTwice;
    }
    
    public boolean discover(AbstractNode node, Edge edge) {
    	if (discoveringTimes.containsKey(node) ||
    			ignoredEdges.contains(edge))
    	{
    		if(discoveringTimes.containsKey(node) && node.equals(root))
    		{
    			reVisitTwice = true;
    		}
    		return false;
    	}
    	
        discoveringTimes.put(node, time++);
        return true;
    }
    
    public void finish(AbstractNode node) {
        finishingTimes.put(node, time++);
    }
    
    public boolean discovered(AbstractNode node) {
        return discoveringTimes.containsKey(node);
    }
    
    /**
     * Indicates an edge that should be ignored during the search.
     * @param edge	an edge that should be ignored
     */
    public void addIgnoredEdge(Edge edge) {
    	ignoredEdges.add(edge);
    }
    
    public final Map<AbstractNode, Integer> discoveringTimes =
        new HashMap<AbstractNode, Integer>();
    public final Map<AbstractNode, Integer> finishingTimes =
        new HashMap<AbstractNode, Integer>();
    
    private int time = 0;
    private Set<Edge> ignoredEdges = new HashSet<Edge>();
}
