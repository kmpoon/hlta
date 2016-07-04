package org.latlab.io.bif;

import java.awt.Color;
import java.awt.Point;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.latlab.graph.AbstractNode;
import org.latlab.io.BeliefNodeProperties;
import org.latlab.io.BeliefNodeProperty;
import org.latlab.io.Writer;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.FunctionIterator;
import org.latlab.util.Variable;

/**
 * Writes Bayesian networks in BIF format.
 * 
 * @author leonard
 * 
 */
public class BifWriter implements Writer {

	/**
	 * Constructs this writer with an underlying output stream, using the
	 * default UTF-8 encoding.
	 * 
	 * @param output
	 *            output stream where the network is written to.
	 * @throws UnsupportedEncodingException
	 */
	public BifWriter(OutputStream output) throws UnsupportedEncodingException {
		this(output, true, "UTF-8");
	}

	/**
	 * Constructs this writer with an underlying output stream, using the
	 * default UTF-8 encoding.
	 * 
	 * @param output
	 *            output stream where the network is written to.
	 * @param useTableFormat
	 *            whether to use table format in probability definition
	 * @throws UnsupportedEncodingException
	 */
	public BifWriter(OutputStream output, boolean useTableFormat)
			throws UnsupportedEncodingException {
		this(output, useTableFormat, "UTF-8");
	}

	/**
	 * Constructs this writer with an underlying output stream.
	 * 
	 * @param output
	 *            output stream where the network is written to.
	 * @param useTableFormat
	 *            whether to use table format in probability definition
	 * @param encoding
	 *            charset used for the output.
	 * @throws UnsupportedEncodingException
	 */
	public BifWriter(OutputStream output, boolean useTableFormat,
			String encoding) throws UnsupportedEncodingException {
		this.useTableFormat = useTableFormat;
		writer = new PrintWriter(new OutputStreamWriter(output, encoding));
	}

	/**
	 * Writes the network.
	 */
	public void write(BayesNet network) {
		write(network, null);
	}

	public void write(BayesNet network, BeliefNodeProperties nodeProperties) {
		write(network, nodeProperties, null);
	}

	public void write(BayesNet network, BeliefNodeProperties nodeProperties,
			DataSet data) {
		writeNetworkDeclaration(network);
		writeVariables(network, nodeProperties);
		writeProbabilities(network);
		if (data != null)
			writeScore(network, data);
		writer.close();
	}

	/**
	 * Writes the network declaration.
	 * 
	 * @param network
	 *            network to write.
	 */
	private void writeNetworkDeclaration(BayesNet network) {
		writer.format("network \"%s\" {\n}\n", network.getName());
		writer.println();
	}

	/**
	 * Writes the variables part.
	 * 
	 * @param network
	 *            network to write.
	 */
	private void writeVariables(BayesNet network,
			BeliefNodeProperties nodeProperties) {
		LinkedList<AbstractNode> nodes = network.getNodes();
		for (AbstractNode node : nodes) {
			BeliefNode beliefNode = (BeliefNode) node;

			// get the node property
			BeliefNodeProperty property = null;
			if (nodeProperties != null)
				property = nodeProperties.get(beliefNode);

			writeNode(beliefNode, property);
		}
	}

	/**
	 * Writes the information of a belief node.
	 * 
	 * @param node
	 *            node to write.
	 */
	private void writeNode(BeliefNode node, BeliefNodeProperty property) {
		ArrayList<String> states = node.getVariable().getStates();

		writer.format("variable \"%s\" {\n", node.getName());

		// write the states
		writer.format("\ttype discrete[%d] { ", states.size());
		for (String state : states) {
			writer.format("\"%s\" ", state);
		}
		writer.println("};");

		writeProperties(node, property);

		writer.println("}");
		writer.println();
	}

	private void writeProperties(BeliefNode node, BeliefNodeProperty property) {
		// write the position if necessary
		if (property != null) {
			Point point = property.getPosition();
			if (point != null) {
				writer.format("\tproperty \"position = (%d, %d)\";\n", point.x,
						point.y);
			}

			int angle = property.getRotation();
			if (angle != BeliefNodeProperty.DEFAULT_ROTATION) {
				writer.format("\tproperty \"rotation = %d\";\n", angle);
			}

			BeliefNodeProperty.FrameType frame = property.getFrame();
			if (frame != BeliefNodeProperty.DEFAULT_FRAME_TYPE) {
				if (frame == BeliefNodeProperty.FrameType.NONE) {
					writer.write("\tproperty \"frame = none\";\n");
				} else if (frame == BeliefNodeProperty.FrameType.OVAL) {
					writer.write("\tproperty \"frame = oval\";\n");
				} else if (frame == BeliefNodeProperty.FrameType.RECTANGLE) {
					writer.write("\tproperty \"frame = rectangle\";\n");
				}
			}

			String label = property.getLabel();
			if (label != null) {
				writer.format("\tproperty \"label = '%s'\";\n", label);
			}

			Color color = property.getForeColor();
			if (color != null) {
				writer.format("\tproperty \"foreColor = (%d, %d, %d)\";\n",
						color.getRed(), color.getGreen(), color.getBlue());
			}

			color = property.getBackColor();
			if (color != null) {
				writer.format("\tproperty \"backColor = (%d, %d, %d)\";\n",
						color.getRed(), color.getGreen(), color.getBlue());
			}

			color = property.getLineColor();
			if (color != null) {
				writer.format("\tproperty \"lineColor = (%d, %d, %d)\";\n",
						color.getRed(), color.getGreen(), color.getBlue());
			}

			String font = property.getFontName();
			if (font != null) {
				writer.format("\tproperty \"font = '%s'\";\n", font);
			}

			int fontSize = property.getFontSize();
			if (fontSize > 0) {
				writer.format("\tproperty \"fontSize = %d\";\n", fontSize);
			}
		}

	}

	/**
	 * Writes the probabilities definition part.
	 * 
	 * @param network
	 *            network to write.
	 */
	private void writeProbabilities(BayesNet network) {
		LinkedList<AbstractNode> nodes = network.getNodes();
		for (AbstractNode node : nodes) {
			writeProbabilities((BeliefNode) node);
		}
	}

	/**
	 * Writes the probabilities definition for a belief node.
	 * 
	 * @param node
	 *            node to write.
	 */
	private void writeProbabilities(BeliefNode node) {
		Function function = node.getCpt();

		ArrayList<Variable> variables = node.getNodeAndParentVariables();

		// write the related variables
		writer.format("probability (\"%s\"", variables.get(0).getName());

		// check if it has parent variables
		if (variables.size() > 1) {
			writer.print(" | ");
			for (int i = 1; i < variables.size(); i++) {
				writer.format("\"%s\"", variables.get(i).getName());
				if (i != variables.size() - 1) {
					writer.print(", ");
				}
			}
		}

		writer.println(") {");

		if (useTableFormat)
			writeProbabilitiesTable(function, variables);
		else
			writeProbabilitiesWithStates(function, variables);

		writer.println("}");
		writer.println();
	}

	private void writeProbabilitiesTable(Function function,
			ArrayList<Variable> variables) {
		double[] cells = function.getCells(variables);
		writer.print("\ttable ");
		for (int i = 0; i < cells.length; i++) {
			writer.print(cells[i]);
			if (i != cells.length - 1) {
				writer.print(" ");
			}
		}
		writer.println(";");
	}

	private void writeProbabilitiesWithStates(Function function,
			ArrayList<Variable> variables) {
		// use table format for root variable
		if (variables.size() == 1) {
			writeProbabilitiesTable(function, variables);
			return;
		}

		// put the parent variables at the beginning for iteration
		ArrayList<Variable> order = new ArrayList<Variable>(variables.size());
		for (int i = 1; i < variables.size(); i++)
			order.add(variables.get(i));
		order.add(variables.get(0));

		FunctionIterator iterator = new FunctionIterator(function, order);
		iterator.iterate(new StateVisitor());
	}

	private void writeScore(BayesNet network, DataSet data) {
		writer.println();
		writer.format("//Loglikelihood: %f\n", network.getLoglikelihood(data));
		writer.format("//BIC Score: %f\n", network.getBICScore(data));
		writer.println();
	}

	/**
	 * The print writer encapsulating the underlying output stream.
	 */
	private final PrintWriter writer;

	private boolean useTableFormat = true;

	private class StateVisitor implements FunctionIterator.Visitor {
		public void visit(List<Variable> order, int[] states, double value) {
			// the node state and variable (instead of parent variables)
			int nodeState = states[states.length - 1];
			Variable nodeVariable = order.get(states.length - 1);

			if (nodeState == 0) {
				writeStart(order, states);
			}

			writer.print(value);

			if (nodeState == nodeVariable.getCardinality() - 1) {
				writeEnd();
			} else {
				writer.print(" ");
			}
		}

		private void writeStart(List<Variable> order, int[] states) {
			writer.print("\t(");
			// write parent states, which excludes the last state
			for (int i = 0; i < states.length - 1; i++) {
				String stateName = order.get(i).getStates().get(states[i]);
				writer.format("\"%s\"", stateName);

				if (i < states.length - 2) {
					writer.write(" ");
				}
			}

			writer.print(") ");
		}

		private void writeEnd() {
			writer.println(";");
		}
	}
}
