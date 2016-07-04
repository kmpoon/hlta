package org.latlab.io;

import org.latlab.graph.Edge;
import org.latlab.model.BeliefNode;

/**
 * Holds the properties of a Bayesian network, including those of
 * nodes and probability definitions.
 * 
 * It is responsible to read the property strings found in the
 * the network file and generate those strings for writing to the file.
 * @author leonard
 *
 */
public class Properties {
    public void readNetworkProperty(String value) {
        
    }
    
    public void readVariableProperty(BeliefNode node, String value) {
        
    }
    
    /**
     * Gets the property of a belief node, and creates a new property
     * if it does not exist for the node.
     * @param node  node whose property is returned
     * @return  property of the specified node
     */
    public BeliefNodeProperty getBeliefNodeProperty(BeliefNode node) {
        BeliefNodeProperty property = nodeProperties.get(node);
        
        if (property == null) {
            property = new BeliefNodeProperty();
            nodeProperties.put(node, property);
        }
        
        return property;
    }
    
    public DependencyEdgeProperty getDependencyEdgeProperty(Edge edge){
    	DependencyEdgeProperty property = edgeProperties.get(edge);
    	
    	if(property == null){
    		property = new DependencyEdgeProperty();
    		edgeProperties.put(edge, property);
    	}
    	
    	return property;
    }
    
    public BeliefNodeProperties getBeliefNodeProperties() {
        return nodeProperties;
    }
    
    public DependencyEdgeProperties getDependencyEdgeProperties(){
    	return edgeProperties;
    }
    
   // private final NetworkProperty networkProperty = new NetworkProperty();
    private final BeliefNodeProperties nodeProperties = 
        new BeliefNodeProperties();
    private final DependencyEdgeProperties edgeProperties =
    	new DependencyEdgeProperties();
}
