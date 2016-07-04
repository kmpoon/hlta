package org.latlab.io;

import org.latlab.model.BayesNet;

/**
 * Writer interface
 * 
 * @author leonard
 * 
 */
public interface Writer {
	/**
	 * Writes a network to the embedded output stream.
	 * 
	 * @param network
	 *            network to be written
	 */
	void write(BayesNet network);

	/**
	 * Writes a network to the embedded output stream, along with some
	 * properties for the nodes in the network.
	 * 
	 * @param network
	 *            network to be written.
	 * @param nodeProperties
	 *            properties of some nodes in the network.
	 */
	void write(BayesNet network, BeliefNodeProperties nodeProperties);
}
