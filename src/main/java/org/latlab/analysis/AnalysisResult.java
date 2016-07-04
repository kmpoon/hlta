package org.latlab.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.model.BayesNet;
import org.latlab.util.Function;
import org.latlab.util.Variable;

/**
 * This class implements the data structure for storing analysis results.
 * 
 * @author wangyi
 * 
 */
public class AnalysisResult extends CmiTree {

	/**
	 * Loads the analysis result from the specified file.
	 * 
	 * @param filename
	 *            The input file.
	 * @param model
	 *            The Bayes net under analysis.
	 * @return The analysis result loaded from the specified file.
	 * @throws IOException
	 */
	static AnalysisResult load(String filename, BayesNet model)
			throws IOException {
		// reader in UTF-8 encoding
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename), "UTF8"));

		AnalysisResult result = new AnalysisResult();

		String[] tokens;

		// read CCPDs
		String line = in.readLine();
		while (line.startsWith("% CCPDs")) {
			Map<Variable, Function> ccpds = new HashMap<Variable, Function>();

			while (!(line = in.readLine()).startsWith("%")) {
				tokens = line.split(" ");
				Variable manifest = model.getNodeByName(tokens[0])
						.getVariable();
				Function ccpd = Function.createUniformDistribution(manifest);
				double[] cells = ccpd.getCells();
				for (int i = 1; i < tokens.length; i++) {
					cells[i - 1] = Double.parseDouble(tokens[i]);
				}
				ccpds.put(manifest, ccpd);
			}

			result._ccpds.add(ccpds);
		}

		// read CMI tree
		result.parsePreOrderString(in.readLine(), model);

		// read largest maximum CMI percent
		in.readLine();
		result._largestMaxCmiPercent = Double.parseDouble(in.readLine());

		// read natural order of manifest variables
		in.readLine();
		tokens = in.readLine().split(" ");
		for (String token : tokens) {
			result._order.add(model.getNodeByName(token).getVariable());
		}

		// read PMI
		in.readLine();
		while (!(line = in.readLine()).startsWith("%")) {
			tokens = line.split(" ");
			result._pmi.put(model.getNodeByName(tokens[0]).getVariable(),
					Double.parseDouble(tokens[1]));
		}

		// read prior distribution
		tokens = in.readLine().split(" ");
		ArrayList<Variable> latents = new ArrayList<Variable>();
		for (String token : tokens) {
			latents.add(model.getNodeByName(token).getVariable());
		}
		result._prior = Function.createFunction(latents);

		tokens = in.readLine().split(" ");
		ArrayList<Double> cells = new ArrayList<Double>();
		for (String token : tokens) {
			cells.add(Double.parseDouble(token));
		}
		result._prior.setCells(latents, cells);

		// read selected manifest variables
		in.readLine();
		tokens = in.readLine().split(" ");
		for (String token : tokens) {
			result._selectedManifests.add(model.getNodeByName(token)
					.getVariable());
		}

		// read selected maximum CMI percent
		in.readLine();
		result._selectedMaxCmiPercent = Double.parseDouble(in.readLine());

		// read TMI
		in.readLine();
		result._tmi = Double.parseDouble(in.readLine());

		return result;
	}

	/**
	 * The CCPD of each manifest variable in each latent state.
	 */
	List<Map<Variable, Function>> _ccpds;

	/**
	 * The largest maximum CMI percent that has been reached so far.
	 */
	double _largestMaxCmiPercent;

	/**
	 * The natural order of the manifest variables w.r.t. the latent variable
	 * set.
	 */
	List<Variable> _order;

	/**
	 * The pairwise MI between the latent variable set and each manifest
	 * variable.
	 */
	Map<Variable, Double> _pmi;

	/**
	 * The prior distribution of the latent variable set.
	 */
	Function _prior;

	/**
	 * The list of manifest variables that are currently selected for
	 * interpreting the latent variable set.
	 */
	List<Variable> _selectedManifests;

	/**
	 * The maximum CMI percent that is currently selected for interpreting the
	 * latent variable set.
	 */
	double _selectedMaxCmiPercent;

	/**
	 * The total MI between the latent variable set and all manifest variables.
	 */
	double _tmi;

	/**
	 * Constructor.
	 */
	AnalysisResult() {
		super();

		_ccpds = new ArrayList<Map<Variable, Function>>();
		_order = new ArrayList<Variable>();
		_pmi = new HashMap<Variable, Double>();
		_selectedManifests = new ArrayList<Variable>();
	}

	/**
	 * Returns the cardinality of the compound latent variable. The cardinality
	 * is the product of the cardinalities of the individual latent variables.
	 * 
	 * @return The cardinality of the compound latent variable.
	 */
	public int getLatentCardinality() {
		return _prior.getDomainSize();
	}

	/**
	 * Returns the name of the compound latent variable. The name is the
	 * concatenation of the names of the individual latent variables.
	 * 
	 * @return The name of the compound latent variable.
	 */
	public String getLatentName() {
		StringBuffer str = new StringBuffer();

		for (Variable latent : _prior.getVariables()) {
			str.append(latent.getName());
		}

		return str.toString();
	}

	/**
	 * Returns the set of latent variables.
	 * 
	 * @return The set of latent variables.
	 */
	Set<Variable> getLatents() {
		Set<Variable> latents = new HashSet<Variable>();

		for (Variable latent : _prior.getVariables()) {
			latents.add(latent);
		}

		return latents;
	}

	/**
	 * Returns the natural order of the manifest variables w.r.t. to the latent
	 * variable set.
	 * 
	 * @return The natural order of the manifest variables w.r.t. to the latent
	 *         variable set.
	 */
	public List<Variable> getNaturalOrder() {
		return _order;
	}

	/**
	 * Returns the prior distribution of the latent variable set.
	 * 
	 * @return The prior distribution of the latent variable set.
	 */
	public Function getPrior() {
		return _prior;
	}

	/**
	 * Returns the CCPDs of the currently selected manifest variables in the
	 * specified latent state.
	 * 
	 * @param index
	 *            The index of the latent state whose CCPDs are to be returned.
	 * @return The CCPDs of the currently selected manifest variables in the
	 *         specified latent state.
	 */
	public List<Function> getSelectedCcpds(int index) {
		List<Function> selectedCcpds = new ArrayList<Function>();
		Map<Variable, Function> ccpds = _ccpds.get(index);

		for (Variable manifest : _selectedManifests) {
			selectedCcpds.add(ccpds.get(manifest));
		}

		return selectedCcpds;
	}

	/**
	 * Returns the list of cumulative MI for the currently selected manifest
	 * variables.
	 * 
	 * @return The list of cumulative MI for the currently selected manifest
	 *         variables.
	 */
	public List<Double> getSelectedCmiSeq() {
		return getMaxSubPathValues(_selectedManifests);
	}

	/**
	 * Returns the list of manifest variables that are currently selected for
	 * interpreting the latent variable set.
	 * 
	 * @return The list of manifest variables that are currently selected.
	 */
	public List<Variable> getSelectedManifests() {
		return _selectedManifests;
	}

	/**
	 * Returns the currently selected maximum CMI percent.
	 * 
	 * @return The currently selected maximum CMI percent.
	 */
	public double getSelectedMaxCmiPercent() {
		return _selectedMaxCmiPercent;
	}

	/**
	 * Returns the list of pairwise MI for the currently selected manifest
	 * variables.
	 * 
	 * @return The list of pairwise MI for the currently selected manifest
	 *         variables.
	 */
	public List<Double> getSelectedPmiSeq() {
		List<Double> pmiSeq = new ArrayList<Double>();

		for (Variable manifest : _selectedManifests) {
			pmiSeq.add(_pmi.get(manifest));
		}

		return pmiSeq;
	}

	/**
	 * Returns the name of the specified latent state. The name will be in the
	 * format of Y1=y1, Y2=y2, ...
	 * 
	 * @param index
	 *            The index of the latent state.
	 * @return The name of the specified latent state.
	 */
	public String getStateName(int index) {
		StringBuffer str = new StringBuffer();

		// lists of variables and states
		List<Variable> latents = _prior.getVariables();
		int nLatents = _prior.getDimension();
		int[] states = new int[nLatents];
		_prior.computeStates(index, states);

		// string representation
		for (int i = 0; i < nLatents; i++) {
			Variable latent = latents.get(i);

			str.append(latent.getName());
			str.append("=");
			str.append(latent.getStates().get(states[i]));

			if (i < nLatents - 1) {
				str.append(", ");
			}
		}

		return str.toString();
	}

	/**
	 * Returns the TMI between the latent variable set and the set of all
	 * manifest variables.
	 * 
	 * @return The TMI between the latent variable set and the set of all
	 *         manifest variables.
	 */
	public double getTmi() {
		return _tmi;
	}

	/**
	 * Saves the analysis result to the specified file.
	 * 
	 * @param filename
	 *            The output file.
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	void save(String filename) throws FileNotFoundException,
			UnsupportedEncodingException {
		// print writer in UTF-8 encoding
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(filename), "UTF8")));

		// output CCPDs
		for (int i = 0; i < _ccpds.size(); i++) {
			out.println("% CCPDs for State " + getStateName(i));

			Map<Variable, Function> ccpds = _ccpds.get(i);
			for (Variable manifest : ccpds.keySet()) {
				out.print(manifest.getName());
				for (double prob : ccpds.get(manifest).getCells()) {
					out.print(" " + prob);
				}
				out.println();
			}
		}

		// output CMI tree
		out.println("% CMI Tree in Pre-Order");
		out.println(toPreOrderString());

		// output largest maximum CMI percent
		out.println("% Largest Maximum CMI Percent");
		out.println(_largestMaxCmiPercent);

		// output natural order of manifest variables
		out.println("% Natural Order of Manifest Variables");
		for (Variable manifest : _order) {
			out.print(manifest.getName() + " ");
		}
		out.println();

		// output PMI values
		out.println("% PMI");
		for (Variable manifest : _pmi.keySet()) {
			out.println(manifest.getName() + " " + _pmi.get(manifest));
		}

		// output prior distribution of latent variables
		out.println("% Prior Distribution");

		for (Variable latent : _prior.getVariables()) {
			out.print(latent.getName() + " ");
		}
		out.println();

		for (double prob : _prior.getCells()) {
			out.print(prob + " ");
		}
		out.println();

		// output currently selected manifest variables
		out.println("% Selected Manifest Variables");
		for (Variable manifest : _selectedManifests) {
			out.print(manifest.getName() + " ");
		}
		out.println();

		// output currently selected maximum CMI percent
		out.println("% Selected Maximum CMI Percent");
		out.println(_selectedMaxCmiPercent);

		// output total MI
		out.println("% TMI");
		out.println(_tmi);

		out.close();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(0);

		// append latent variable names
		for (Variable latent : _prior.getVariables()) {
			str.append(latent.getName());
			str.append(" ");
		}

		// append latent states and prior distribution
		str.append("[");

		List<Variable> latents = _prior.getVariables();
		int nLatents = _prior.getDimension();
		int[] states = new int[nLatents];
		int card = getLatentCardinality();

		for (int i = 0; i < card; i++) {
			_prior.computeStates(i, states);

			for (int j = 0; j < nLatents; j++) {
				str.append(latents.get(j).getStates().get(states[j]));
			}

			if (i < card - 1) {
				str.append(";");
			}
		}

		str.append("|");

		double[] prior = _prior.getCells();

		for (int i = 0; i < card; i++) {
			str.append(nf.format(prior[i] * 100));

			if (i < card - 1) {
				str.append(";");
			}
		}

		str.append("]: ");

		// append the list of selected manifest variables
		int nManifests = _selectedManifests.size();
		List<Double> pmiSeq = getSelectedPmiSeq();
		List<Double> cmiSeq = getSelectedCmiSeq();

		for (int i = 0; i < nManifests; i++) {
			Variable manifest = _selectedManifests.get(i);

			// append manifest variable name
			str.append(manifest.getName());
			str.append(" [");

			// append class-conditional probability P(X=1|Y=j)
			for (int j = 0; j < card; j++) {
				str
						.append(nf.format(_ccpds.get(j).get(manifest)
								.getCells()[1] * 100));

				if (j < card - 1) {
					str.append(";");
				}
			}

			str.append("|");

			// append pairwise and cumulative information coverage
			str.append(nf.format(pmiSeq.get(i) * 100 / _tmi));
			str.append(";");
			str.append(nf.format(cmiSeq.get(i) * 100 / _tmi));

			str.append("]");
			if (i < nManifests - 1) {
				str.append(", ");
			}
		}

		return str.toString();
	}

}
