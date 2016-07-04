/**
 * BayesNet.java 
 * Copyright (C) 2006 Tao Chen, Kin Man Poon, Yi Wang, and Nevin L. Zhang
 */
package org.latlab.model;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedAcyclicGraph;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.TreeChecker;
import org.latlab.graph.predicates.RootNodePredicate;
import org.latlab.util.Algorithm;
import org.latlab.util.Caster;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Variable;

/**
 * This class provides an implementation for Bayes nets (BNs).
 * 
 * @author Yi Wang
 * 
 */
public class BayesNet extends DirectedAcyclicGraph {

	/**
	 * the prefix of default names of BNs.
	 */
	private final static String NAME_PREFIX = "BayesNet";

	/**
	 * the number of created BNs.
	 */
	private static int _count = 0;

	/**
	 * Creates a BN that is defined by the specified file.
	 * 
	 * @param file
	 *            file that defines a BN.
	 * @return the BN defined by the specified file.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public final static BayesNet createBayesNet(String file) throws IOException {
		BayesNet bayesNet = new BayesNet(createDefaultName());

		// only supports BIF format by now
		bayesNet.loadBif(file);

		return bayesNet;
	}

	/**
	 * Returns the default name for the next BN.
	 * 
	 * @return the default name for the next BN.
	 */
	public final static String createDefaultName() {
		return NAME_PREFIX + _count;
	}

	/**
	 * the name of this BN.
	 */
	protected String _name;

	/**
	 * the map from variables to nodes.
	 */
	protected HashMap<Variable, BeliefNode> _variables;


	public final boolean containsVars(Collection<Variable> vars) {
		return _variables.keySet().containsAll(vars);
	}

	public final boolean containsVar(Variable var) {
		return _variables.keySet().contains(var);
	}

	
	/**
	 * the map from data sets to loglikelihoods of this BN on them.
	 * loglikelihoods will expire once the structure or the parameters of this
	 * BN change.
	 */
	protected HashMap<DataSet, Double> _loglikelihoods;

	/**
	 * Constructs an empty BN.
	 * 
	 */
	public BayesNet() {
		this(createDefaultName());
	}

	/**
	 * Constructs an empty BN with the specified name.
	 * 
	 * @param name
	 *            name of this BN.
	 */
	public BayesNet(String name) {
		super();

		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		_name = name;
		_variables = new HashMap<Variable, BeliefNode>();
		_loglikelihoods = new HashMap<DataSet, Double>();

		_count++;
	}

	/**
	 * Adds an edge that connects the two specified nodes to this BN and returns
	 * the edge. This implementation extends
	 * <code>AbstractGraph.addEdge(AbstractNode, AbstractNode)</code> such
	 * that all loglikelihoods will be expired.
	 * 
	 * The resulting edge is {@code head <- tail}.
	 * 
	 * @param head
	 *            head of the edge.
	 * @param tail
	 *            tail of the edge.
	 * @return the edge that was added to this BN.
	 */
	public final Edge addEdge(AbstractNode head, AbstractNode tail) {
		Edge edge = super.addEdge(head, tail);

		// loglikelihoods expire
		expireLoglikelihoods();

		return edge;
	}

	/**
	 * This implementation is no long supported in <code>BayesNet</code>. Use
	 * <code>addNode(Variable)</code> instead.
	 * 
	 * @see addNode(Variable)
	 */
	public final BeliefNode addNode(String name)
			throws UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Adds a node with the specified variable attached to this BN and returns
	 * the node.
	 * 
	 * @param variable
	 *            variable to be attached to the node.
	 * @return the node that was added to this BN.
	 */
	public final BeliefNode addNode(Variable variable) {
		// name must be unique in this BN. note that the name is unique implies
		// that the variable is unique, too. note also that the name of a
		// variable has already been trimmed.
		assert !containsNode(variable.getName());

		// creates node
		BeliefNode node = new BeliefNode(this, variable);

		// adds node to the list of nodes in this BN
		_nodes.add(node);

		// maps name to node
		_names.put(variable.getName(), node);

		// maps variable to node
		_variables.put(variable, node);

		// loglikelihoods expire
		expireLoglikelihoods();

		return node;
	}

	/**
	 * Creates and returns a deep copy of this BN. This implementation copies
	 * everything in this BN but the name and variables. The default name will
	 * be used for the copy instead of the original one. The variables will be
	 * reused other than deeply copied. This will facilitate learning process.
	 * However, one cannot change node names after clone. TODO avoid redundant
	 * operations on CPTs.
	 * <p>
	 * Also note that cpts are also cloned.
	 * </p>
	 * 
	 * @return a deep copy of this BN.
	 */
	@SuppressWarnings("unchecked")
	public BayesNet clone() {
		BayesNet copy = new BayesNet(createDefaultName());

		// copies nodes
		for (AbstractNode node : _nodes) {
			copy.addNode(((BeliefNode) node).getVariable());
		}

		// copies edges
		for (Edge edge : _edges) {
			copy.addEdge(copy.getNode(edge.getHead().getName()), copy
					.getNode(edge.getTail().getName()));
		}
		// copies CPTs
		for (AbstractNode node : copy._nodes) {
			BeliefNode bNode = (BeliefNode) node;
			bNode.setCpt(getNode(bNode.getVariable()).getCpt().clone());
		}

		// copies loglikelihoods
		copy._loglikelihoods = (HashMap<DataSet, Double>) _loglikelihoods
				.clone();

		return copy;
	}

	/**
	 * Returns the standard dimension, namely, the number of free parameters in
	 * the CPTs, of this BN.
	 * 
	 * @return the standard dimension of this BN.
	 */
	public final int computeDimension() {
		// sums up dimension for each node
		int dimension = 0;

		for (AbstractNode node : _nodes) {
			dimension += ((BeliefNode) node).computeDimension();
		}

		return dimension;
	}

	/**
	 * <p>
	 * Makes loglikelihoods evaluated for this BN expired.
	 * </p>
	 * 
	 * <p>
	 * <b>Note: Besides methods in this class, only
	 * <code>BeliefNode.setCpt(Function)</code> is supposed to call this
	 * method.
	 * </p>
	 * 
	 * @see BeliefNode#setCpt(Function)
	 */
	protected final void expireLoglikelihoods() {
		if (!_loglikelihoods.isEmpty()) {
			_loglikelihoods.clear();
		}
	}

	/**
	 * Get AICc Score of this BayesNet w.r.t data. Note that the loglikelihood
	 * of the model has already been computed and been stored in this model. (We
	 * should improve this point later.)
	 * 
	 * @author wangyi
	 * @param data
	 * @return AICc Score computed.
	 */
	public final double getAICcScore(DataSet data) {
		// TODO We will deal with the expiring case later.
		double logL = this.getLoglikelihood(data);

		assert logL != Double.NaN;

		// c.f. http://en.wikipedia.org/wiki/Akaike_information_criterion
		int k = computeDimension();
		return logL - k - k * (k + 1) / (data.getTotalWeight() - k - 1);
	}

	/**
	 * Get AIC Score of this BayesNet w.r.t data. Note that the loglikelihood of
	 * the model has already been computed and been stored in this model. (We
	 * should improve this point later.)
	 * 
	 * @author wangyi
	 * @param data
	 * @return AIC Score computed.
	 */
	public final double getAICScore(DataSet data) {
		// TODO We will deal with the expiring case later.
		double logL = this.getLoglikelihood(data);

		assert logL != Double.NaN;

		return logL - this.computeDimension();
	}

	/**
	 * Get BIC Score of this BayesNet w.r.t data. Note that the loglikelihood of
	 * the model has already been computed and been stored in this model.(We
	 * should improve this point later.)
	 * 
	 * @author csct Added by Chen Tao
	 * @param data
	 * @return BIC Score computed.
	 */
	public final double getBICScore(DataSet data) {

		// TODO We will deal with the expiring case later.
		double logL = this.getLoglikelihood(data);

		assert logL != Double.NaN;

		return logL - this.computeDimension() * Math.log(data.getTotalWeight())
				/ 2.0;
	}

	/**
	 * Returns the set of internal variables.
	 * 
	 * @return The set of internal variables.
	 */
	public final Set<Variable> getInternalVars() {
		Set<Variable> vars = new HashSet<Variable>();
		BayesNet model = clone();
		
		if(TreeChecker.isTree(model))
		{
			for (Variable var : getVariables()) {
				if (!getNode(var).isLeaf())
					vars.add(var);
			}
		}else if(TreeChecker.isBayesNet(model))
		{
			//if model is a bayesNet but not a tree, return the roots.
			List<DirectedNode> roots = Algorithm.filter(model.getNodes(),
					new Caster<DirectedNode>(), new RootNodePredicate());
			
			for(DirectedNode node : roots)
			{
				vars.add(((BeliefNode) node).getVariable());
			}
		}
		return vars;
	}

	/**
	 * Get the internalVars as indicated
	 * @return
	 */
	public final Set<Variable> getInternalVars(String input) {
		Set<Variable> vars = new HashSet<Variable>();
		BayesNet model = clone();
		
		if(input.equals("tree"))
		{
			for (Variable var : getVariables()) {
				if (!getNode(var).isLeaf())
					vars.add(var);
			}
		}else if(input.equals("BayesNet"))
		{
			//if model is a bayesNet but not a tree, return the roots.
			List<DirectedNode> roots = Algorithm.filter(model.getNodes(),
					new Caster<DirectedNode>(), new RootNodePredicate());
			
			for(DirectedNode node : roots)
			{
				vars.add(((BeliefNode) node).getVariable());
			}
		}
		return vars;
	}

	/**
	 * Returns the set of leaf variables.
	 * 
	 * @return The set of leaf variables.
	 */
	public final Set<Variable> getLeafVars() {
		Set<Variable> vars = new HashSet<Variable>();
		BayesNet model = clone();
		
		if(TreeChecker.isTree(model))
		{
			for (Variable var : getVariables()) {
				if (getNode(var).isLeaf())
					vars.add(var);
			}
		}else if(TreeChecker.isBayesNet(model))
		{
			//if model is a bayesNet but not a tree, return the non-root nodes.
			List<DirectedNode> roots = Algorithm.filter(model.getNodes(),
					new Caster<DirectedNode>(), new RootNodePredicate());
			
			for(AbstractNode node : model.getNodes())
			{
				if(!roots.contains(node))
				{
					vars.add(((BeliefNode) node).getVariable());
				}
			}
		}
		
		return vars;
	}
	
	
    /**
     * Enforce to get leaf node as indicated
     * @param input
     * @return
     */
	public final Set<Variable> getLeafVars(String input) {
		Set<Variable> vars = new HashSet<Variable>();
		BayesNet model = clone();
		
		if(input.equals("tree"))
		{
			for (Variable var : getVariables()) {
				if (getNode(var).isLeaf())
					vars.add(var);
			}
		}else if(input.equals("BayesNet"))
		{
			//if model is a bayesNet but not a tree, return the non-root nodes.
			List<DirectedNode> roots = Algorithm.filter(model.getNodes(),
					new Caster<DirectedNode>(), new RootNodePredicate());
			
			for(AbstractNode node : model.getNodes())
			{
				if(!roots.contains(node))
				{
					vars.add(((BeliefNode) node).getVariable());
				}
			}
		}
		
		return vars;
	}

	/**
	 * Return the loglikelihood of this BN with respect to the specified data
	 * set.
	 * 
	 * @param dataSet
	 *            data set at request.
	 * @return the loglikelihood of this BN with respect to the specified data
	 *         set; return <code>Double.NaN</code> if the loglikelihood has
	 *         not been evaluated yet.
	 */
	public final double getLoglikelihood(DataSet dataSet) {
		Double loglikelihood = _loglikelihoods.get(dataSet);

		return loglikelihood == null ? Double.NaN : loglikelihood;
	}

	/**
	 * Returns the name of this BN.
	 * 
	 * @return the name of this BN.
	 */
	public final String getName() {
		return _name;
	}

	/**
	 * Gets a belief node from this network by name.
	 * 
	 * @param name
	 *            name of the target node
	 * @return node with the specified name, or null if not found
	 */
	public BeliefNode getNodeByName(String name) {
		return (BeliefNode) super.getNode(name);
	}

	/**
	 * Returns the node to which the specified variable is attached in this BN.
	 * 
	 * @param variable
	 *            variable attached to the node.
	 * @return the node to which the specified variable is attached; returns
	 *         <code>null</code> if none uses this variable.
	 */
	public final BeliefNode getNode(Variable variable) {
		return _variables.get(variable);
	}

	/**
	 * Returns the list of variables in this BN. For the sake of efficiency,
	 * this implementation returns the reference to the private field. Make sure
	 * you understand this before using this method.
	 * 
	 * @return the list of variables in this BN.
	 */
	public final Set<Variable> getVariables() {

		Set<Variable> vars = new HashSet<Variable>();
		for (Variable var : _variables.keySet()) {
			vars.add(var);
		}
		return vars;
		// return _variables.keySet();
	}

	/**
	 * Loads the specified BIF file.
	 * 
	 * @param file
	 *            BIF file that defines a BN.
	 */
	private final void loadBif(String file) throws IOException {
		StreamTokenizer tokenizer = new StreamTokenizer(new FileReader(file));

		tokenizer.resetSyntax();

		// characters that will be ignored
		tokenizer.whitespaceChars('=', '=');
		tokenizer.whitespaceChars(' ', ' ');
		tokenizer.whitespaceChars('"', '"');
		tokenizer.whitespaceChars('\t', '\t');

		// word characters
		tokenizer.wordChars('A', 'z');

		// we will parse numbers
		tokenizer.parseNumbers();

		// special characters considered in the gramma
		tokenizer.ordinaryChar(';');
		tokenizer.ordinaryChar('(');
		tokenizer.ordinaryChar(')');
		tokenizer.ordinaryChar('{');
		tokenizer.ordinaryChar('}');
		tokenizer.ordinaryChar('[');
		tokenizer.ordinaryChar(']');

		// does NOT treat eol as a token
		tokenizer.eolIsSignificant(false);

		// ignores c++ comments
		tokenizer.slashSlashComments(true);

		// starts parsing
		int value;

		// reads until the end of the stream (file)
		do {
			value = tokenizer.nextToken();

			if (value == StreamTokenizer.TT_WORD) {
				// start of a new block here
				String word = tokenizer.sval;

				if (word.equals("network")) {
					// parses network properties. next string must be the name
					// of this BN
					tokenizer.nextToken();
					setName(tokenizer.sval);
				} else if (word.equals("variable")) {
					// parses variable. get name of variable first
					tokenizer.nextToken();
					String name = tokenizer.sval;

					// looks for '['
					do {
						value = tokenizer.nextToken();
					} while (value != '[');

					// gets integer as cardinality
					tokenizer.nextToken();
					int cardinality = (int) tokenizer.nval;

					// looks for '{'
					do {
						value = tokenizer.nextToken();
					} while (value != '{');

					// state list
					ArrayList<String> states = new ArrayList<String>();

					// gets states
					do {
						value = tokenizer.nextToken();
						if (value == StreamTokenizer.TT_WORD) {
							states.add(tokenizer.sval);
						}
					} while (value != '}');

					// tests consistency
					assert states.size() == cardinality;

					// creates node
					addNode(new Variable(name, states));
				} else if (word.equals("probability")) {
					// parses CPT. skips next '('
					tokenizer.nextToken();

					// variables in this family
					ArrayList<Variable> family = new ArrayList<Variable>();

					// gets variable name and node
					tokenizer.nextToken();
					BeliefNode node = (BeliefNode) getNode(tokenizer.sval);
					family.add(node.getVariable());

					// gets parents and adds edges
					do {
						value = tokenizer.nextToken();
						if (value == StreamTokenizer.TT_WORD) {
							BeliefNode parent = (BeliefNode) getNode(tokenizer.sval);
							family.add(parent.getVariable());

							// adds edge from parent to node
							addEdge(node, parent);
						}
					} while (value != ')');

					// creates CPT
					Function cpt = Function.createFunction(family);

					// looks for '(' or words
					do {
						value = tokenizer.nextToken();
					} while (value != '(' && value != StreamTokenizer.TT_WORD);

					// checks next token: there are two formats, one with
					// "table" and the other fills in cells one by one.
					if (value == StreamTokenizer.TT_WORD) {
						// we only accept "table" but not "default"
						assert tokenizer.sval.equals("table");

						// probability values
						ArrayList<Double> values = new ArrayList<Double>();

						// gets numerical tokens
						do {
							value = tokenizer.nextToken();
							if (value == StreamTokenizer.TT_NUMBER) {
								values.add(tokenizer.nval);
							}
						} while (value != ';');

						// consistency between family and values will be tested
						cpt.setCells(family, values);
					} else {
						// states array
						ArrayList<Integer> states = new ArrayList<Integer>();
						states.add(0);
						int cardinality = node.getVariable().getCardinality();

						// parses row by row
						while (value != '}') {
							// gets parent states
							for (int i = 1; i < family.size(); i++) {
								do {
									value = tokenizer.nextToken();
								} while (value != StreamTokenizer.TT_WORD);
								states.add(family.get(i)
										.indexOf(tokenizer.sval));
							}

							// fills in data
							for (int i = 0; i < cardinality; i++) {
								states.set(0, i);

								do {
									value = tokenizer.nextToken();
								} while (value != StreamTokenizer.TT_NUMBER);
								cpt.setCell(family, states, tokenizer.nval);
							}

							// looks for next '(' or '}'
							while (value != '(' && value != '}') {
								value = tokenizer.nextToken();
							}
						}
					}

					// normalizes the CPT with respect to the attached variable
					cpt.normalize(node.getVariable());

					// sets the CPT
					node.setCpt(cpt);
				}
			}
		} while (value != StreamTokenizer.TT_EOF);
	}

	/**
	 * Randomly sets the parameters of this BN. TODO avoid redundant operations
	 * on CPTs.
	 */
	public final void randomlyParameterize() {
		for (AbstractNode node : _nodes) {
			((BeliefNode) node).randomlyParameterize();
		}

		// loglikelihoods expire
		expireLoglikelihoods();
	}

	/**
	 * Randomly sets the parameters of the specified list of nodes in this BN.
	 * TODO avoid redundant operations on CPTs.
	 * 
	 * @param mutableNodes
	 *            list of nodes whose parameters are to be randomized.
	 */
	public final void randomlyParameterize(Collection<BeliefNode> mutableNodes) {
		// mutable nodes must be in this BN
		assert _nodes.containsAll(mutableNodes);

		for (BeliefNode node : mutableNodes) {
			node.randomlyParameterize();
		}

		// loglikelihoods expire
		expireLoglikelihoods();
	}

	/**
	 * Removes the specified edge from this BN. This implementation extends
	 * <code>AbstractGraph.removeEdge(Edge)</code> such that all
	 * loglikelihoods will be expired.
	 * 
	 * @param edge
	 *            edge to be removed from this BN.
	 */
	@Override
	public final void removeEdge(Edge edge) {
		super.removeEdge(edge);

		// loglikelihoods expire
		expireLoglikelihoods();
	}

	/**
	 * Removes the specified node from this BN. This implementation extends
	 * <code>AbstractGraph.removeNode(AbstractNode)</code> such that map from
	 * variables to nodes will be updated and all loglikelihoods will be
	 * expired.
	 * 
	 * @param node
	 *            node to be removed from this BN.
	 */
	public final void removeNode(AbstractNode node) {
		super.removeNode(node);

		// removes variable from map
		_variables.remove(((BeliefNode) node).getVariable());

		// loglikelihoods expire
		expireLoglikelihoods();
	}
	
	/**
	 * Replace the latVar by the newVar. Parameters concerning the new node are
	 * not properly defined.
	 * 
	 * @param latVar
	 * @param newVar
	 * @return
	 */
	public BayesNet manipulation_VariableReplacement(Variable latVar, Variable newVar) {
		BayesNet template = clone();
		ArrayList<BeliefNode> mutableNodesInTemplate = new ArrayList<BeliefNode>();
		
		BeliefNode latNode = template.getNode(latVar);
		BeliefNode newNode = template.addNode(newVar);

		Set<DirectedNode> children = latNode.getChildren();
		BeliefNode parent = (BeliefNode) latNode.getParent();
		for (DirectedNode child : children) {
			BeliefNode beliefChild = (BeliefNode) child;
			template.addEdge(beliefChild, newNode);
			
			mutableNodesInTemplate.add( (BeliefNode) child);
		}

		if (parent != null) {
			template.addEdge(newNode, parent);
		
			mutableNodesInTemplate.add(newNode);
		}
		template.removeNode(latNode);
		
		template.randomlyParameterize(mutableNodesInTemplate);
		
		return template;
	}

	/**
	 * Generates a batch of samples from this BN.
	 * 
	 * @param sampleSize
	 *            number of samples to be generated.
	 * @return a batch of samples from this BN.
	 */
	public DataSet sample(int sampleSize) {
		// initialize data set
		int nNodes = getNumberOfNodes();
		DataSet samples = new DataSet(getVariables().toArray(
				new Variable[nNodes]));

		// since variables are sorted in data set, find mapping from belief
		// nodes to variables
		Variable[] vars = samples.getVariables();
	//added by peixian
		Arrays.sort(vars);
		HashMap<AbstractNode, Integer> map = new HashMap<AbstractNode, Integer>();
		for (AbstractNode node : getNodes()) {
			Variable var = ((BeliefNode) node).getVariable();
			int pos = Arrays.binarySearch(vars, var);
			map.put(node, pos);
		}

		// topological sort
		AbstractNode[] order = topologicalSort();

		for (int i = 0; i < sampleSize; i++) {
			int[] states = new int[nNodes];

			// forward sampling
			for (AbstractNode node : order) {
				BeliefNode bNode = (BeliefNode) node;

				// find parents and their states
				ArrayList<Variable> parents = new ArrayList<Variable>();
				ArrayList<Integer> parentStates = new ArrayList<Integer>();
				for (DirectedNode parent : bNode.getParents()) {
					Variable var = ((BeliefNode) parent).getVariable();
					parents.add(var);

					int pos = map.get(parent);
					parentStates.add(states[pos]);
				}

				// instantiate parents
				Function cond = bNode.getCpt().project(parents, parentStates);

				// sample according to the conditional distribution
				states[map.get(node)] = cond.sample();
			}

			// add to samples
			samples.addDataCase(states, 1.0);
		}

		return samples;
	}

	/**
	 * Outputs this BN to the specified file in BIF format. See
	 * http://www.cs.cmu.edu/~fgcozman/Research/InterchangeFormat/Old/xmlbif02.html
	 * for the grammar of BIF format.
	 * 
	 * @param file
	 *            output of this BN.
	 * @throws FileNotFoundException
	 *             if the file exists but is a directory rather than a regular
	 *             file, does not exist but cannot be created, or cannot be
	 *             opened for any other reason.
	 * @throws UnsupportedEncodingException 
	 */
	public final void saveAsBif(String file) throws FileNotFoundException, UnsupportedEncodingException {
//		PrintWriter out = new PrintWriter(file);
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(file), "UTF8")));

		// outputs header
		out.println("// " + file);
		out.println("// Produced by org.latlab at "
				+ (new Date(System.currentTimeMillis())));

		// outputs name
		out.println("network \"" + _name + "\" {");
		out.println("}");
		out.println();

		// outputs nodes
		for (AbstractNode node : _nodes) {
			Variable variable = ((BeliefNode) node).getVariable();

			// name of variable
			out.println("variable \"" + variable.getName() + "\" {");

			// states of variable
			out.print("\ttype discrete[" + variable.getCardinality() + "] { ");
			Iterator<String> iter = variable.getStates().iterator();
			while (iter.hasNext()) {
				out.print("\"" + iter.next() + "\"");
				if (iter.hasNext()) {
					out.print(" ");
				}
			}
			out.println(" };");

			out.println("}");
			out.println();
		}

		// Output CPTs
		for (AbstractNode node : _nodes) {
			BeliefNode bNode = (BeliefNode) node;

			// variables in this family. note that the variables in the
			// probability block are arranged from the most significant place to
			// the least significant place.
			ArrayList<Variable> vars = new ArrayList<Variable>();
			vars.add(bNode.getVariable());

			// name of node
			out.print("probability ( \"" + bNode.getName() + "\" ");

			// names of parents
			if (!bNode.isRoot()) {
				out.print("| ");
			}

			Iterator<DirectedNode> iter = bNode.getParents().iterator();
			while (iter.hasNext()) {
				BeliefNode parent = (BeliefNode) iter.next();

				out.print("\"" + parent.getName() + "\"");
				if (iter.hasNext()) {
					out.print(", ");
				}

				vars.add(parent.getVariable());
			}
			out.println(" ) {");

			// cells in CPT
			out.print("\ttable");
			for (double cell : bNode.getCpt().getCells(vars)) {
				out.print(" " + cell);
			}
			out.println(";");
			out.println("}");
		}

		out.close();
	}

	/**
	 * Replaces the loglikelihood of this BN with respect to the specified data
	 * set.
	 * 
	 * @param dataSet
	 *            data set at request.
	 * @param loglikelihood
	 *            new loglikelihood of this BN.
	 */
	public final void setLoglikelihood(DataSet dataSet, double loglikelihood) {
		// loglikelihood must be non-positive
		assert loglikelihood <= 0.0;

		_loglikelihoods.put(dataSet, loglikelihood);
	}

	/**
	 * Replaces the name of this BN.
	 * 
	 * @param name
	 *            new name of this BN.
	 */
	public final void setName(String name) {
		name = name.trim();

		// name cannot be blank
		assert name.length() > 0;

		_name = name;
	}

	/**
	 * Returns a string representation of this BN. The string representation
	 * will be indented by the specified amount.
	 * 
	 * @param amount
	 *            amount by which the string representation is to be indented.
	 * @return a string representation of this BN.
	 */
	public String toString(int amount) {
		// amount must be non-negative
		assert amount >= 0;

		// prepares white space for indent
		StringBuffer whiteSpace = new StringBuffer();
		for (int i = 0; i < amount; i++) {
			whiteSpace.append('\t');
		}

		// builds string representation
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append(whiteSpace);
		stringBuffer.append(getName() + " {\n");

		stringBuffer.append(whiteSpace);
		stringBuffer
				.append("\tnumber of nodes = " + getNumberOfNodes() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tnodes = {\n");

		for (AbstractNode node : _nodes) {
			stringBuffer.append(node.toString(amount + 2));
		}

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\t};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer
				.append("\tnumber of edges = " + getNumberOfEdges() + ";\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\tedges = {\n");

		for (Edge edge : _edges) {
			stringBuffer.append(edge.toString(amount + 2));
		}

		stringBuffer.append(whiteSpace);
		stringBuffer.append("\t};\n");

		stringBuffer.append(whiteSpace);
		stringBuffer.append("};\n");

		return stringBuffer.toString();
	}

}
