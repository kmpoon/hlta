package org.latlab.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.TrialVersionLimitException;
import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.latlab.util.DataSet.DataCase;

/**
 * This class provides tools for analyzing latent structure models.
 * 
 * @author wangyi
 * 
 */
public class AnalysisEngine {

	/**
	 * Default maximum CMI percent.
	 */
	public final static double DEFAULT_MAX_CMI_PERCENT = 0.95;

	/**
	 * Default number of samples for approximating CMI.
	 */
	public final static int DEFAULT_SAMPLE_SIZE = 10000;

	/**
	 * Analysis result for each latent variable set. Together with
	 * _analyzedLatents, this member provides quick access to analysis results.
	 */
	private Map<Set<Variable>, AnalysisResult> _analysisResults;

	/**
	 * The list of latent variable sets that have been analyzed so far. Together
	 * with _analysisResults, this member provides quick access to analysis
	 * results.
	 */
	private List<Set<Variable>> _analyzedLatents;

	/**
	 * Maximum CMI percent.
	 */
	private double _maxCmiPercent;

	/**
	 * Model to analyze.
	 */
	private BayesNet _model;

	/**
	 * The CTP for computing posterior distributions.
	 */
	private CliqueTreePropagation _posteriorCtp;

	/**
	 * The CTP for computing prior distributions. This CTP will propagate only
	 * once throughout the analysis procedure.
	 */
	private CliqueTreePropagation _priorCtp;

	/**
	 * Samples for approximating CMI.
	 */
	private DataSet _samples;

	/**
	 * Number of samples for approximation CMI.
	 */
	private int _sampleSize;

	/**
	 * Constructor.
	 * 
	 * @param model
	 *            The model to analyze.
	 */
	public AnalysisEngine(BayesNet model) {
		if (TrialVersionLimitException.TRIAL_VERSION
				&& model.getLeafVars().size() > TrialVersionLimitException.MAX_NUM_MANIFEST) {
			throw new TrialVersionLimitException();
		}

		_model = model;
		_maxCmiPercent = DEFAULT_MAX_CMI_PERCENT;
		_sampleSize = DEFAULT_SAMPLE_SIZE;
	}

	/**
	 * Analyzes the specified latent variable set.
	 * 
	 * @param latents
	 *            The latent variable set to be analyzed.
	 */
	public void analyze(Collection<Variable> latents) {
		// only executes for latent variable set that has not be analyzed yet
		if (!hasAnalyzed(latents)) {
			// initialize data structure
			Set<Variable> latentSet = new HashSet<Variable>(latents);
			_analysisResults.put(latentSet, new AnalysisResult());
			_analyzedLatents.add(latentSet);

			// heavy computation
			computePrior(latents);
			computePmi(latents);
			computeNaturalOrder(latents);
			computeTmi(latents);
			computeCmiSeq(latents, _maxCmiPercent);
			computeCcpds(latents);
		}
	}

	/**
	 * Analyzes all individual latent variables. One must call this method
	 * before any other analysis method. A second call to this method erases all
	 * existing analysis results.
	 */
	public void analyzeAllIndividualLatents() {
		// initialization
		initialize(true);

		// analyze each individual latent variable
		for (Variable latent : _model.getInternalVars()) {
			Collection<Variable> latents = new HashSet<Variable>();
			latents.add(latent);
			analyze(latents);
		}
	}

	/**
	 * Computes and stores the CCPDs of each manifest variable with respect to
	 * the specified latent variable set.
	 * 
	 * @param latents
	 *            The latent variable set for which the CCPDs are to be
	 *            computed.
	 */
	private void computeCcpds(Collection<Variable> latents) {
		AnalysisResult result = getAnalysisResult(latents);

		// a function for relating latent states and their indices
		Function index = Function.createFunction(new ArrayList<Variable>(
				latents));

		// latent variable array and their states
		int nLatents = index.getDimension();
		Variable[] latentArray = index.getVariables().toArray(
				new Variable[nLatents]);
		int[] states = new int[nLatents];

		// consider each latent state
		for (int i = 0; i < index.getDomainSize(); i++) {
			Map<Variable, Function> ccpd = new HashMap<Variable, Function>();

			// convert index to latent state
			index.computeStates(i, states);

			// set evidence for latent state
			_posteriorCtp.setEvidence(latentArray, states);
			_posteriorCtp.propagate();

			// compute posterior for each manifest variable
			for (Variable manifest : _model.getLeafVars()) {
				Function posterior = _posteriorCtp.computeBelief(manifest);
				ccpd.put(manifest, posterior);
			}

			result._ccpds.add(ccpd);
		}
	}

	/**
	 * Computes and stores the CMI sequence for the specified latent variable
	 * set until the cumulative information coverage reaches the specified
	 * threshold.
	 * 
	 * One must run <code>analyze(latents)</code> once before calling this
	 * method.
	 * 
	 * @param latents
	 *            The latent variable set whose CMI sequence is to be computed.
	 * @param maxCmiPercent
	 *            The threshold on the cumulative information coverage.
	 */
	public void computeCmiSeq(Collection<Variable> latents, double maxCmiPercent) {
		AnalysisResult result = getAnalysisResult(latents);

		List<Variable> order = result._order;
		List<Double> cmiSeq = result.getMaxSubPathValues(order);
		double tmi = result._tmi;

		// if the desired information coverage exceeds what we have so far, we
		// need to do extra computation; otherwise, we simply select the subset
		// of manifest variables that satisfy the threshold
		if (maxCmiPercent > result._largestMaxCmiPercent) {
			for (int i = cmiSeq.size(); i < order.size(); i++) {
				// include one more variable each iteration
				List<Variable> manifests = order.subList(0, i + 1);

				double cmi = computeMi(latents, manifests);
				cmiSeq.add(cmi);

				// terminate if we reach the desired information coverage
				double cmiPercent = cmi / tmi;
				if (cmiPercent >= maxCmiPercent) {
					result.putPathValues(manifests, cmiSeq);
					result._largestMaxCmiPercent = cmiPercent;
					result._selectedManifests = manifests;
					break;
				}
			}
		} else {
			// find the subset of manifest variables that satisfies the
			// threshold
			for (int i = 0; i < cmiSeq.size(); i++) {
				if (cmiSeq.get(i) / tmi >= maxCmiPercent) {
					result._selectedManifests = order.subList(0, i + 1);
					break;
				}
			}
		}

		result._selectedMaxCmiPercent = maxCmiPercent;
	}

	/**
	 * Computes and stores the CMI sequence for the specified latent variable
	 * set w.r.t. the specified list of manifest variables. Denote the latent
	 * variable set by Y and the list of manifest variables by X1, X2, ..., Xn.
	 * The CMI sequence is I(Y; X1), I(Y; X1--X2), ..., I(Y; X1--Xn).
	 * 
	 * One must run <code>analyze(latents)</code> once before calling this
	 * method.
	 * 
	 * @param latents
	 *            The latent variable set whose CMI sequence is to be computed.
	 * @param manifests
	 *            The list of manifest variables for which the CMI sequence is
	 *            to be computed.
	 */
	public void computeCmiSeq(Collection<Variable> latents,
			List<Variable> manifests) {
		AnalysisResult result = getAnalysisResult(latents);

		if (!result.contains(manifests)) {
			List<Double> cmiSeq = result.getMaxSubPathValues(manifests);
			int nManifests = manifests.size();

			for (int i = cmiSeq.size(); i < nManifests; i++) {
				cmiSeq.add(computeMi(latents, manifests.subList(0, i + 1)));
			}

			result.putPathValues(manifests, cmiSeq);

			// if the list of manifest variables happens to be a prefix of the
			// natural order, we have to update the largest maximum CMI percent
			// as well
			if (result._order.subList(0, nManifests).equals(manifests)) {
				result._largestMaxCmiPercent = cmiSeq.get(nManifests - 1)
						/ result._tmi;
			}
		}

		result._selectedManifests = manifests;
	}

	/**
	 * Computes the MI between two sets of variables x and y. If |x| = 1 and |y|
	 * = 1, we calculate the exact MI; Otherwise, we do sampling to approximate
	 * the MI.
	 * 
	 * @param x
	 *            A set of random variables.
	 * @param y
	 *            Another set of random variables.
	 * @return The MI I(x; y).
	 */
	private double computeMi(Collection<Variable> x, Collection<Variable> y) {
		// exact computation in case of |x| = 1 and |y| = 1
		if (x.size() == 1 && y.size() == 1) {
			List<Variable> xyNodes = new ArrayList<Variable>();
			xyNodes.add(x.iterator().next());
			xyNodes.add(y.iterator().next());

			return Utils.computeMutualInformation(_priorCtp
					.computeBelief(xyNodes));
		}

		// xy = x union y
		ArrayList<Variable> xy = new ArrayList<Variable>();
		xy.addAll(x);
		xy.addAll(y);

		// samples over xy
		DataSet xySamples = _samples.project(xy);

		// array of xy
		Variable[] xyArray = xySamples.getVariables();
		int xySize = xyArray.length;

		// locate x and y in xyArray, respectively
		List<Integer> xPos = new ArrayList<Integer>();
		for (Variable var : x) {
			xPos.add(Arrays.binarySearch(xyArray, var));
		}

		List<Integer> yPos = new ArrayList<Integer>();
		for (Variable var : y) {
			yPos.add(Arrays.binarySearch(xyArray, var));
		}

		// initialization
		double mi = 0.0;
		int[] states = new int[xySize];
		double pxy, px, py;

		// I(x; y) = \sum_{x, y} P(x, y) log P(x, y) / P(x)P(y) \approx \sum_{x,
		// y} Q(x, y) log P(x, y) / P(x)P(y), where Q(x, y) is the empirical
		// distribution induced by the samples
		for (DataCase d : xySamples.getData()) {
			// compute P(dx, dy)
			_posteriorCtp.setEvidence(xyArray, d.getStates());
			pxy = _posteriorCtp.propagate();

			// compute P(dx)
			System.arraycopy(d.getStates(), 0, states, 0, xySize);
			for (int pos : yPos) {
				states[pos] = DataSet.MISSING_VALUE; // hide values of y
			}
			_posteriorCtp.setEvidence(xyArray, states);
			px = _posteriorCtp.propagate();

			// compute P(dy)
			System.arraycopy(d.getStates(), 0, states, 0, xySize);
			for (int pos : xPos) {
				states[pos] = DataSet.MISSING_VALUE; // hide values of x
			}
			_posteriorCtp.setEvidence(xyArray, states);
			py = _posteriorCtp.propagate();

			// increase MI by weight * log (pxy / (px * py))
			double increase = d.getWeight() * Math.log(pxy / (px * py));
			if (Double.isInfinite(increase) || Double.isNaN(increase)) {
				// numeric error
				System.err.println("Skip!");
			} else {
				mi += increase;
			}
		}

		// divided by the sample size
		mi /= xySamples.getTotalWeight();

		return mi;
	}

	/**
	 * Computes and stores the natural order of manifest variables for the
	 * specified latent variable set. The natural order is an descending order
	 * with respect to the PMI.
	 * 
	 * @param latents
	 *            The latent variable set whose natural order is to be computed.
	 */
	private void computeNaturalOrder(Collection<Variable> latents) {
		final AnalysisResult result = getAnalysisResult(latents);

		// sort all manifest variables
		result._order.addAll(_model.getLeafVars());

		Collections.sort(result._order, new Comparator<Variable>() {

			public int compare(Variable var1, Variable var2) {
				double pmi1 = result._pmi.get(var1);
				double pmi2 = result._pmi.get(var2);

				if (pmi1 > pmi2) {
					return -1;
				} else if (pmi1 < pmi2) {
					return 1;
				} else {
					return 0;
				}
			}

		});
	}

	/**
	 * Computes and stores the pairwise MI between the specified latent variable
	 * set and each manifest variable.
	 * 
	 * @param latents
	 *            The latent variable set whose PMI is to be computed.
	 */
	private void computePmi(Collection<Variable> latents) {
		AnalysisResult result = getAnalysisResult(latents);

		List<Variable> manifests = new ArrayList<Variable>();
		manifests.add(null);

		for (Variable manifest : _model.getLeafVars()) {
			manifests.set(0, manifest);
			result._pmi.put(manifest, computeMi(latents, manifests));
		}
	}

	/**
	 * Computes and stores the prior distribution of the specified latent
	 * variable set. The computation is done exactly.
	 * 
	 * @param latents
	 *            The latent variable set whose prior distribution is to be
	 *            computed.
	 */
	private void computePrior(Collection<Variable> latents) {
		// find the corresponding belief nodes
		Collection<Variable> nodes = new ArrayList<Variable>();
		for (Variable latent : latents) {
			nodes.add(latent);
		}

		// compute prior distribution
		AnalysisResult result = getAnalysisResult(latents);
		result._prior = _priorCtp.computeBelief(nodes);
	}

	/**
	 * Computes and stores the total MI between the specified latent variable
	 * set and all manifest variables.
	 * 
	 * @param latents
	 *            The latent variable set whose TMI is to be computed.
	 */
	private void computeTmi(Collection<Variable> latents) {
		AnalysisResult result = getAnalysisResult(latents);
		result._tmi = computeMi(latents, _model.getLeafVars());
	}

	/**
	 * Returns the analysis result for the specified latent variable set, or
	 * <code>null</code> if the specified latent variable set has not been
	 * analyzed yet.
	 * 
	 * @param latents
	 *            The latent variable set whose analysis result is to be
	 *            returned.
	 * @return The analysis result for the specified latent variable set.
	 */
	public AnalysisResult getAnalysisResult(Collection<Variable> latents) {
		int index = _analyzedLatents.indexOf(new HashSet<Variable>(latents));
		if (index < 0) {
			return null;
		} else {
			return _analysisResults.get(_analyzedLatents.get(index));
		}
	}

	/**
	 * Returns the list of latent variable sets that have been analyzed so far.
	 * 
	 * @return The list of latent variable sets that have been analyzed so far.
	 */
	public List<Set<Variable>> getAnalyzedLatents() {
		return _analyzedLatents;
	}

	/**
	 * Returns the maximum CMI percent.
	 * 
	 * @return The maximum CMI percent.
	 */
	public double getMaxCmiPercent() {
		return _maxCmiPercent;
	}

	/**
	 * Returns the number of samples for approximating CMI.
	 * 
	 * @return The number of samples for approximating CMI.
	 */
	public int getSampleSize() {
		return _sampleSize;
	}

	/**
	 * Returns <code>true</code> if and only if the specified latent variable
	 * set has been analyzed.
	 * 
	 * @param latents
	 *            The latent variable set the availability of whose analysis
	 *            result is to be tested.
	 * @return <code>true</code> if and only if the specified latent variable
	 *         set has been analyzed.
	 */
	public boolean hasAnalyzed(Collection<Variable> latents) {
		int index = _analyzedLatents.indexOf(new HashSet<Variable>(latents));
		if (index < 0) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Initialization.
	 * 
	 * @param toSample
	 *            Generates samples or not.
	 */
	private void initialize(boolean toSample) {
		// data structures for storing analysis results
		_analysisResults = new HashMap<Set<Variable>, AnalysisResult>();
		_analyzedLatents = new ArrayList<Set<Variable>>();

		// inference engines
		_posteriorCtp = new CliqueTreePropagation((LTM)_model);
		_priorCtp = new CliqueTreePropagation((LTM)_model);
		_priorCtp.propagate();

		// generate samples when required
		if (toSample) {
			_samples = _model.sample(_sampleSize);
		}
	}

	/**
	 * Load everything from the specified path.
	 * 
	 * @param path
	 *            The input path.
	 * @throws IOException
	 */
	public void load(String path) throws IOException {
		// initialize
		initialize(false);

		// load settings
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(path + File.separator
						+ "settings"), "UTF8"));

		in.readLine();
		_maxCmiPercent = Double.parseDouble(in.readLine());

		in.readLine();
		_sampleSize = Integer.parseInt(in.readLine());

		// load samples
		_samples = new DataSet(path + File.separator + "samples");
		_samples = _samples.synchronize(_model);

		// load analysis results
		File dir = new File(path);

		for (String filename : dir.list()) {
			if (filename.equals("settings") || filename.equals("samples")) {
				continue;
			}

			AnalysisResult result = AnalysisResult.load(path + File.separator
					+ filename, _model);

			Set<Variable> latents = result.getLatents();
			_analysisResults.put(latents, result);
			_analyzedLatents.add(latents);
		}
	}

	/**
	 * Removes the analysis result for the specified collection of latent
	 * variables.
	 * 
	 * @param latents
	 *            The collection of latent variables whose analysis result is to
	 *            be removed.
	 */
	public void removeAnalysisResult(Collection<Variable> latents) {
		int index = _analyzedLatents.indexOf(new HashSet<Variable>(latents));
		if (index >= 0) {
			_analysisResults.remove(_analyzedLatents.get(index));
			_analyzedLatents.remove(index);
		}
	}

	/**
	 * Saves settings, samples, and analysis results to the specified path.
	 * 
	 * @param path
	 *            The output path.
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public void save(String path) throws FileNotFoundException,
			UnsupportedEncodingException {
		// save settings
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(path
						+ File.separator + "settings"), "UTF8")));

		out.println("% Maximum CMI Percent");
		out.println(_maxCmiPercent);

		out.println("% Sample Size");
		out.println(_sampleSize);

		out.close();

		// save samples
		_samples.save(path + File.separator + "samples");

		// save analysis results
		for (Set<Variable> latents : _analyzedLatents) {
			// file name
			StringBuffer name = new StringBuffer();
			for (Variable latent : latents) {
				name.append(latent.getName());
			}

			getAnalysisResult(latents).save(
					path + File.separator + name.toString());
		}
	}

	/**
	 * Sets the maximum CMI percent.
	 * 
	 * @param maxCmiPercent
	 *            The new maximum CMI percent.
	 */
	public void setMaxCmiPercent(double maxCmiPercent) {
		assert maxCmiPercent > 0.0 && maxCmiPercent <= 1.0;
		_maxCmiPercent = maxCmiPercent;
	}

	/**
	 * Sets the number of samples for approximating CMI.
	 * 
	 * @param sampleSize
	 *            The new number of samples for approximating CMI.
	 */
	public void setSampleSize(int sampleSize) {
		assert sampleSize > 0;
		_sampleSize = sampleSize;
	}

}
