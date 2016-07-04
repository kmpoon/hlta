package org.latlab.graph.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.latlab.graph.AbstractGraph;
import org.latlab.graph.AbstractNode;
import org.latlab.graph.Edge;

/**
 * Visitor for finding a path between two nodes in a graph.
 * To perform a path search, use the source node as the start node
 * in a depth first search with this visitor.  After the search
 * has completed, this visitor holds the path from source to destination.
 * @author leonard
 *
 */
public class PathFindingVisitor extends AbstractVisitor {
	public PathFindingVisitor(AbstractNode destination) {
		this.destination = destination;
	}

	public boolean discover(AbstractNode node, Edge edge) {
		if (visited.contains(node))
			return false;
		else
			visited.add(node);
		
		// stops the search once the destination has been discovered
		if (discovered)
			return false;
		
		path.add(node);
		
		if (node == destination) {
			discovered = true;
			return false;
		} else
			return true;
	}

	public void finish(AbstractNode node) {
		// if the destination has not been discovered, rewind the path
		if (!discovered) {
			AbstractNode last = path.remove(path.size() - 1);
			assert last == node;
		}
	}
	
	public List<AbstractNode> getPath() {
		return path;
	}
	
	/**
	 * A helper method for finding the path from source to destination.
	 * @param graph			graph on which the path is searched
	 * @param source		source node
	 * @param destintation	destination node
	 * @return				path from source to destination in the graph
	 */
	public static List<AbstractNode> findPath(AbstractGraph graph,
			AbstractNode source, AbstractNode destintation) {
		DepthFirstSearch search = new DepthFirstSearch(graph);
		PathFindingVisitor visitor = new PathFindingVisitor(destintation);
		search.perform(source, visitor);
		return visitor.getPath();
	}

	private final AbstractNode destination;
	private final HashSet<AbstractNode> visited = new HashSet<AbstractNode>();
	private final ArrayList<AbstractNode> path = new ArrayList<AbstractNode>();
	private boolean discovered = false;
}
