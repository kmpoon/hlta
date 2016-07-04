package org.latlab.graph;

import java.util.List;

import org.latlab.graph.predicates.RootNodePredicate;
import org.latlab.graph.search.DepthFirstSearch;
import org.latlab.graph.search.TimeVisitor;
import org.latlab.util.Algorithm;
import org.latlab.util.Caster;

/**
 * Checks whether a graph is a tree.
 * 
 * @author leonard
 * 
 */
public class TreeChecker {

	/**
	 * Returns whether a graph is a tree.
	 * 
	 * @param graph
	 *            graph to check
	 * @return whether the graph is a tree
	 */
	public static boolean isTree(AbstractGraph graph) {
		// the number of edges in a tree must be number of nodes minus 1
		if (graph.getNumberOfEdges() != graph.getNumberOfNodes() - 1)
			return false;

		// a tree can have only one root
		List<DirectedNode> roots = Algorithm.filter(graph.getNodes(),
				new Caster<DirectedNode>(), new RootNodePredicate());
		if (roots.size() != 1)
			return false;

		// all nodes in a tree must be connected to the root
		DepthFirstSearch search = new DepthFirstSearch(graph);
		TimeVisitor visitor = new TimeVisitor();
		search.perform(roots.get(0), visitor);

		return visitor.discoveringTimes.size() == graph.getNumberOfNodes();
	}
	
	/**
	 * Return whether a graph is a bayesnet.
	 * 
	 * The idea is: if it is not a bayesnet, there must be circles
	 * 
	 * @param graph
	 * @return
	 */
	public static boolean isBayesNet(AbstractGraph graph)
	{	
		
		List<DirectedNode> roots = Algorithm.filter(graph.getNodes(),
				new Caster<DirectedNode>(), new RootNodePredicate());
		
		if(roots.size()==0)
			return false;
		
		DepthFirstSearch search = new DepthFirstSearch(graph);
		TimeVisitor visitor = new TimeVisitor();
		search.perform(roots.get(0), visitor);
		
		if(visitor.discoveringTimes.size() != graph.getNumberOfNodes())
		{
			return false;
		}
		
		for(AbstractNode node : graph.getNodes())
		{
			search = new DepthFirstSearch(graph);
			visitor = new TimeVisitor();
			visitor.setRoot(node);
			search.perform(node, visitor);
			
			
			if(visitor.reVisit())
			{
				return false;
			}
		}
		
		return true;
	}
}
