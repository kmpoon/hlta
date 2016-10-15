package tm.hlta;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.latlab.util.Function;
import org.latlab.util.ScoreCalculator;
import org.latlab.util.Utils;
import org.latlab.util.Variable;

import clustering.EmpiricalMiComputerForBinaryData;

/**
 * Part of PEM code for finding sibling clusters.
 *
 */
public class IslandFinder {
	/**
	 * The collection of hierarchies. Each hierarchy represents a LCM and is
	 * indexed by the variable at its root.
	 */
	private Map<Variable, LTM> _hierarchies;

	/**
	 * The ArrayList of manifest variables with orders.
	 */
	protected ArrayList<Variable> _Variables = new ArrayList<Variable>();

	/**
	 * The collection of manifest variables that wait to do UD-test.
	 */
	protected Set<Variable> _VariablesSet = new HashSet<Variable>();

	/**
	 * The collection of posterior distributions P(Y|d) for each latent variable
	 * Y at the root of a hierarchy and each data case d in the training data.
	 * USELESS in PEM version But keep it for future use if we need soft
	 * assignment or to keep record
	 */
	private Map<Variable, Map<DataCase, Function>> _latentPosts;

	/**
	 * The collection of pairwise mutual information.
	 */
	private Map<Variable, Map<Variable, Double>> _mis;

	/**
	 * Save bestPair of observed variables for every latent variable(LCM)
	 */
	Map<String, ArrayList<Variable>> _bestpairs = new HashMap<String, ArrayList<Variable>>();

	/**
	 * Threshold for EM.
	 */
	protected final double _emThreshold = 0.01;

	/**
	 * Parameter for EM.
	 */
	protected final int _EmMaxSteps = 50;

	/**
	 * Parameter for EM.
	 */
	protected final int _EmNumRestarts = 5;

	/**
	 * Maximum number of island size
	 */
	protected final int _maxIsland = 10;

	/**
	 * Threshold for UD-test.
	 */
	protected final double _UDthreshold = 3;

	protected void initialize(DataSet data) {
		System.out.println("=== Initialization ===");

		// initialize data structures for P(Y|d).
		_latentPosts = new HashMap<Variable, Map<DataCase, Function>>();

		// initialize hierarchies
		// _hirearchies will be used to keep all LCMs found by U-test.
		_hierarchies = new HashMap<Variable, LTM>();

		_VariablesSet = new HashSet<Variable>();

		_mis = new HashMap<Variable, Map<Variable, Double>>();

		// add all manifest variable to variable set _VariableSet.
		for (Variable var : data.getVariables()) {
			_VariablesSet.add(var);
		}
	}

	public Collection<LTM> find(DataSet _data)
			throws FileNotFoundException, UnsupportedEncodingException {

		int i = 1;
		initialize(_data);
		// Call lcmLearner iteratively and learn the LCMs.
		while (!isDone()) {
			System.out.println("======================= Learn Island : " + i
					+ " , number of variables left: " + _VariablesSet.size()
					+ "  =================================");
			if (_VariablesSet.size() == 3) {
				if (_mis.isEmpty()) {
					ArrayList<Variable> bestPair = new ArrayList<Variable>();
					// compute MI and find the pair with the largest MI
					// value
					long startMI = System.currentTimeMillis();
					_mis = computeMis(bestPair, _data);
					System.out.println(
							"======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: "
							+ (System.currentTimeMillis() - startMI) + " ms ---");

				}
				ArrayList<Variable> bestP = new ArrayList<Variable>();
				findBestPair(bestP, _VariablesSet);
				// System.out.println("Best Pair " + bestP.get(0).getName()
				// +" and " + bestP.get(1).getName());
				ArrayList<Variable> Varstemp = new ArrayList<Variable>(_VariablesSet);
				DataSet data_proj = _data.project(Varstemp);
				LTM subModel = LCM3N(Varstemp, data_proj);
				updateHierarchies(subModel, bestP);
				updateVariablesSet(subModel);
				break;
			}

			ArrayList<Variable> bestPair = new ArrayList<Variable>();
			// _mis only needs to compute once

			if (_mis.isEmpty()) {
				// compute MI and find the pair with the largest MI value
				long startMI = System.currentTimeMillis();
				_mis = computeMis(bestPair, _data);
				System.out.println(
						"======================= _mis has been calculated  =================================");
				System.out.println("--- ComputingMI Time: "
						+ (System.currentTimeMillis() - startMI) + " ms ---");
				// System.out.println("Best Pair " +
				// bestPair.get(0).getName() +" and " +
				// bestPair.get(1).getName());

			} else {
				findBestPair(bestPair, _VariablesSet);
				// System.out.println("Best Pair " +
				// bestPair.get(0).getName() +" and " +
				// bestPair.get(1).getName());
			}

			Set<Variable> cluster = new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 3
			// variables now
			ArrayList<Variable> ClosestVariablePair = findShortestOutLink(_mis, null,
					cluster, _VariablesSet);
			ArrayList<Variable> cluster_3n = new ArrayList<Variable>(bestPair);

			// cluster_3n is an array containing 3 variables : bestpair and
			// ClosestVariablePair.get(1)
			LTM subModel = null;
			if (!ClosestVariablePair.isEmpty()) {
				cluster_3n.add(ClosestVariablePair.get(1));
				cluster.add(ClosestVariablePair.get(1));
			}
			// m0
			LTM m0 = LCM3N(cluster_3n, _data.project(cluster_3n));
			// cluster is the working set
			while (true) {
				ClosestVariablePair = findShortestOutLink(_mis, bestPair, cluster,
						_VariablesSet);
				cluster.add(ClosestVariablePair.get(1));
				DataSet data_proj2l = _data.project(new ArrayList<Variable>(cluster));
				LTM m1 = EmLCM_learner(m0, ClosestVariablePair.get(1), bestPair,
						data_proj2l);
				LTM minput = m1.clone();
				LTM m2 = EmLTM_2L_learner(minput, bestPair, ClosestVariablePair,
						data_proj2l);
				m0 = m1.clone();
				double mulModelBIC = ScoreCalculator.computeBic(m2, data_proj2l);
				double uniModelBIC = ScoreCalculator.computeBic(m1, data_proj2l);

				if (mulModelBIC - uniModelBIC > _UDthreshold) {
					if (_VariablesSet.size() - cluster.size() == 0) {
						// split m2 to 2 LCMs subModel1 and subModel2
						LTM subModel1 = m1.clone();
						for (int id = 0; id < 2; id++) {
							Edge e = subModel1.getNode(ClosestVariablePair.get(id))
									.getEdge(subModel1.getRoot());
							// Should remove node first then edge.
							subModel1.removeNode(
									subModel1.getNode(ClosestVariablePair.get(id)));
							subModel1.removeEdge(e);
						}
						// To get subModel2
						HashSet<String> donotUpdate = new HashSet<String>();
						// learn an LCM with ClosestVariablePair and any
						// other
						// one node
						LTM subModel2 = new LTM();
						ArrayList<Variable> cluster_sub2_3node = new ArrayList<Variable>(
								ClosestVariablePair);
						cluster_sub2_3node.add(bestPair.get(1));
						// subModel2 = LTM.createLCM(cluster_sub2_3node, 2);
						subModel2 = LCM3N(cluster_sub2_3node,
								_data.project(cluster_sub2_3node));
						// copy parameters from m2 to submodel2
						ArrayList<Variable> var2s = new ArrayList<Variable>(
								subModel2.getNode(ClosestVariablePair.get(0)).getCpt()
										.getVariables());
						subModel2.getNode(ClosestVariablePair.get(0)).getCpt()
								.setCells(var2s, m2.getNode(ClosestVariablePair.get(0))
										.getCpt().getCells());
						var2s = new ArrayList<Variable>(
								subModel2.getNode(ClosestVariablePair.get(1)).getCpt()
										.getVariables());
						subModel2.getNode(ClosestVariablePair.get(1)).getCpt()
								.setCells(var2s, m2.getNode(ClosestVariablePair.get(1))
										.getCpt().getCells());
						donotUpdate.add(ClosestVariablePair.get(0).getName());
						donotUpdate.add(ClosestVariablePair.get(1).getName());

						ParallelEmLearner emLearner = new ParallelEmLearner();
						emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
						emLearner.setMaxNumberOfSteps(_EmMaxSteps);
						emLearner.setNumberOfRestarts(_EmNumRestarts);
						// fix starting point or not?
						emLearner.setReuseFlag(false);
						emLearner.setThreshold(_emThreshold);
						emLearner.setDontUpdateNodes(donotUpdate);
						subModel2 = (LTM) emLearner.em(subModel2,
								data_proj2l.project(cluster_sub2_3node));

						// remove the edge of other node
						Edge e2 = subModel2.getNode(bestPair.get(1))
								.getEdge(subModel2.getRoot());
						subModel2.removeNode(subModel2.getNode(bestPair.get(1)));
						subModel2.removeEdge(e2);

						updateHierarchies(subModel1, bestPair);
						updateVariablesSet(subModel1);
						updateHierarchies(subModel2, ClosestVariablePair);
						updateVariablesSet(subModel2);
						break;
					} else {
						for (int id = 0; id < 2; id++) {
							Edge e = m1.getNode(ClosestVariablePair.get(id))
									.getEdge(m1.getRoot());
							// Should remove node first then edge.
							m1.removeNode(m1.getNode(ClosestVariablePair.get(id)));
							m1.removeEdge(e);
						}
						updateHierarchies(m1, bestPair);
						updateVariablesSet(m1);
						break;
					}
				} else if (_VariablesSet.size() - cluster.size() == 0
						|| (cluster.size() >= _maxIsland
								&& (_VariablesSet.size() - cluster.size()) >= 3)) {
					subModel = m1;
					updateHierarchies(subModel, bestPair);
					updateVariablesSet(subModel);
					break;
				}
			}
			i++;
		}

		// build the whole latent tree.

		// LTM latentTree = BuildLatentTree(_data);

		return _hierarchies.values();
	}

	/**
	 * Return true if and only if the whole clustering procedure is done, or
	 * equivalently, there is only one hierarchy left.
	 */
	private boolean isDone() {
		return _VariablesSet.size() < 1;
	}

	/**
	 * Update the collection of hierarchies.
	 */
	private void updateHierarchies(LTM subModel, ArrayList<Variable> bestPair) {
		BeliefNode root = subModel.getRoot();
		_bestpairs.put(root.getName(), bestPair);
		// add new hierarchy
		_hierarchies.put(root.getVariable(), subModel);

	}

	/**
	 * Update variable set.
	 * 
	 * @param subModel
	 */
	private void updateVariablesSet(LTM subModel) {
		BeliefNode root = subModel.getRoot();

		for (DirectedNode child : root.getChildren()) {
			_VariablesSet.remove(((BeliefNode) child).getVariable());
			_Variables.remove(((BeliefNode) child).getVariable());
		}
	}

	/**
	 * Learn a 3 node LCM
	 * 
	 */
	private LTM LCM3N(ArrayList<Variable> variables3, DataSet data_proj) {
		LTM LCM_new = LTM.createLCM(variables3, 2);

		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		// fix starting point or not?
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);

		LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(variables3));

		return LCM_new;
	}

	protected Map<Variable, Map<Variable, Double>> computeMis(
			ArrayList<Variable> bestPair, DataSet _data) {
		return computeMisByCount(bestPair, _data);
	}

	protected Map<Variable, Map<Variable, Double>> computeMisByCount(
			ArrayList<Variable> bestPair, DataSet _data) {
		List<Variable> vars = new ArrayList<Variable>(_VariablesSet);

		EmpiricalMiComputerForBinaryData computer = new EmpiricalMiComputerForBinaryData(
				_data, vars);
		ArrayList<double[]> miArray = computer.computerPairwise();

		return processMi(bestPair, miArray, vars);
	}

	/**
	 * Find the closest variable to cluster. Note: Never move the bestpair out
	 * 
	 * @param mis
	 * @param cluster
	 * @return
	 */
	private ArrayList<Variable> findShortestOutLink(
			Map<Variable, Map<Variable, Double>> mis, ArrayList<Variable> bestPair,
			Set<Variable> cluster, Set<Variable> VariablesSet) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			boolean a = bestPair == null;
			if (a || !bestPair.contains(inCluster)) {
				for (Entry<Variable, Double> entry : mis.get(inCluster).entrySet()) {
					Variable outCluster = entry.getKey();
					double mi = entry.getValue();

					// skip variables already in cluster
					if (cluster.contains(outCluster)
							|| !(VariablesSet.contains(outCluster))) {
						continue;
					}

					// keep the variable with max MI.
					if (mi > maxMi) {
						maxMi = mi;
						bestInCluster = inCluster;
						bestOutCluster = outCluster;
					}
				}
			}
		}

		// Set<Variable> ClosestVariablePair = new HashSet<Variable>();
		ArrayList<Variable> ClosestVariablePair = new ArrayList<Variable>();
		ClosestVariablePair.add(bestInCluster);
		ClosestVariablePair.add(bestOutCluster);

		return ClosestVariablePair;
	}

	private LTM EmLCM_learner(LTM modelold, Variable x, ArrayList<Variable> bestPair,
			DataSet data_proj) {

		ArrayList<Variable> cluster3node = new ArrayList<Variable>(bestPair);
		cluster3node.add(x);
		// Learn a 3node LTM : bestpair and newly added node
		LTM LCM3var = LTM.createLCM(cluster3node, 2);
		LCM3var.randomlyParameterize();
		HashSet<String> donotUpdate = new HashSet<String>();

		ArrayList<Variable> var2s = new ArrayList<Variable>(
				LCM3var.getNode(bestPair.get(0)).getCpt().getVariables());
		LCM3var.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(0)).getCpt().getCells());
		donotUpdate.add(bestPair.get(0).getName());
		var2s = new ArrayList<Variable>(
				LCM3var.getNode(bestPair.get(1)).getCpt().getVariables());
		LCM3var.getNode(bestPair.get(1)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(1)).getCpt().getCells());
		donotUpdate.add(bestPair.get(1).getName());
		var2s = new ArrayList<Variable>(LCM3var.getRoot().getCpt().getVariables());
		LCM3var.getRoot().getCpt().setCells(var2s,
				modelold.getRoot().getCpt().getCells());
		donotUpdate.add(LCM3var.getRoot().getName());

		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		// fix starting point or not?
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);
		LCM3var = (LTM) emLearner.em(LCM3var, data_proj.project(cluster3node));

		LTM uniModel = modelold.clone();

		uniModel.addNode(x);

		uniModel.addEdge(uniModel.getNode(x), uniModel.getRoot());
		ArrayList<Variable> vars = new ArrayList<Variable>(
				uniModel.getNode(x).getCpt().getVariables());
		uniModel.getNode(x).getCpt().setCells(vars,
				LCM3var.getNode(x).getCpt().getCells());

		return uniModel;
	}

	/**
	 * 
	 * @param unimodel
	 * @param bestPair
	 * @param ClosestPair
	 * @param data_proj
	 * @return a model with two latent variables (without node relocation step)
	 */

	private LTM EmLTM_2L_learner(LTM unimodel, ArrayList<Variable> bestPair,
			ArrayList<Variable> ClosestPair, DataSet data_proj) {

		ArrayList<Variable> cluster2BeAdded = new ArrayList<Variable>(
				unimodel.getManifestVars());
		ArrayList<Variable> cluster4var = new ArrayList<Variable>(bestPair);

		// construct a LTM with 4 observed variables 2 latent variables
		LTM lCM = new LTM();
		BeliefNode h2 = lCM.addNode(new Variable(2));
		BeliefNode h1 = lCM.addNode(new Variable(2));

		for (Variable var : bestPair) {
			lCM.addEdge(lCM.addNode(var), h1);
			cluster2BeAdded.remove(var);

		}

		for (Variable var : ClosestPair) {
			lCM.addEdge(lCM.addNode(var), h2);
			cluster4var.add(var);
			cluster2BeAdded.remove(var);

		}
		lCM.addEdge(h2, h1);

		// copy parameters of unimodel to m1
		HashSet<String> donotUpdate = new HashSet<String>();
		ArrayList<Variable> var1 = new ArrayList<Variable>(
				lCM.getRoot().getCpt().getVariables());
		lCM.getRoot().getCpt().setCells(var1, unimodel.getRoot().getCpt().getCells());

		ArrayList<Variable> var2s = new ArrayList<Variable>(
				lCM.getNode(bestPair.get(0)).getCpt().getVariables());
		lCM.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				unimodel.getNode(bestPair.get(0)).getCpt().getCells());
		var2s = new ArrayList<Variable>(
				lCM.getNode(bestPair.get(1)).getCpt().getVariables());
		lCM.getNode(bestPair.get(1)).getCpt().setCells(var2s,
				unimodel.getNode(bestPair.get(1)).getCpt().getCells());

		donotUpdate.add(h1.getName());
		donotUpdate.add(bestPair.get(0).getName());
		donotUpdate.add(bestPair.get(1).getName());

		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		// fix starting point or not?
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);

		LTM LTM4var = (LTM) emLearner.em(lCM, data_proj.project(cluster4var));
		// System.out.println("--- Total Time for checking (EM): " +
		// (System.currentTimeMillis() - startcheck) + " ms ---");

		// Add the rest of variables to m1 and copy parameters
		LTM multimodel = LTM4var.clone();
		for (Variable v : cluster2BeAdded) {

			multimodel.addEdge(multimodel.addNode(v), multimodel.getRoot());
			var2s = new ArrayList<Variable>(
					multimodel.getNode(v).getCpt().getVariables());
			multimodel.getNode(v).getCpt().setCells(var2s,
					unimodel.getNode(v).getCpt().getCells());
		}

		return multimodel;
	}

	public class EmpiricalMiComputer {
		private final DataSet data;
		private final List<Variable> variables;
		private final boolean normalize;

		public EmpiricalMiComputer(DataSet data, List<Variable> variables,
				boolean normalize) {
			this.data = data;
			this.normalize = normalize;
			this.variables = variables;
		}

		/**
		 * Computes the mutual information between two discrete variables.
		 * 
		 * @param discretizedData
		 * @param v1
		 * @param v2
		 * @return
		 * @throws Exception
		 */
		protected double compute(Variable vi, Variable vj) {
			Function pairDist = computeEmpDist(Arrays.asList(vi, vj), data);
			double mi = Utils.computeMutualInformation(pairDist);

			// use normalized version of MI.
			if (normalize) {
				// this version used in Strehl & Ghosh (2002)
				double enti = Utils.computeEntropy(pairDist.sumOut(vj));
				double entj = Utils.computeEntropy(pairDist.sumOut(vi));
				if (mi != 0) {
					mi /= Math.sqrt(enti * entj);
				}
			}

			return mi;
		}

		/**
		 * Computes a the mutual information between each pair of variables. It
		 * does not contain any valid value on the diagonal.
		 * 
		 * @param includeClassVariable
		 *            whether to include the class variable
		 * @return mutual information for each pair of variables
		 */
		public double[][] computerPairwise() {
			Implementation implementation = new Implementation();
			implementation.computeParallel();
			return implementation.values;
		}

		/**
		 * Implementation for computing
		 * 
		 * @author kmpoon
		 * 
		 */
		public class Implementation {
			private double[][] values;

			private Implementation() {
				this.values = new double[variables.size()][variables.size()];
			}

			// private void compute() {
			// computeFirstRange(0, variables.size());
			// }

			private void computeParallel() {
				ForkJoinPool pool = new ForkJoinPool();
				pool.invoke(new ParallelComputation(0, variables.size()));
			}

			private void computeFirstRange(int start, int end) {
				for (int i = start; i < end; i++) {
					computeSecondRange(i, i + 1, variables.size());
				}
			}

			private void computeSecondRange(int base, int start, int end) {
				Variable v1 = variables.get(base);
				for (int j = start; j < end; j++) {
					Variable v2 = variables.get(j);
					values[base][j] = compute(v1, v2);
					values[j][base] = values[base][j];
				}
			}

			@SuppressWarnings("serial")
			public class ParallelComputation extends RecursiveAction {

				private final int start;
				private final int end;
				private static final int THRESHOLD = 10;

				private ParallelComputation(int start, int end) {
					this.start = start;
					this.end = end;
				}

				private void computeDirectly() {
					computeFirstRange(start, end);
				}

				@Override
				protected void compute() {
					int length = end - start;
					if (length <= THRESHOLD) {
						computeDirectly();
						return;
					}

					int split = length / 2;
					invokeAll(new ParallelComputation(start, start + split),
							new ParallelComputation(start + split, end));
				}
			}
		}

	}

	/**
	 * Compute the empirical distribution of the given pair of variables
	 */
	private Function computeEmpDist(List<Variable> varPair, DataSet _data) {
		Variable[] vars = _data.getVariables();

		Variable vi = varPair.get(0);
		Variable vj = varPair.get(1);

		int viIdx = -1, vjIdx = -1;

		// retrieve P(Y|d) for latent variables and locate manifest variables
		Map<DataCase, Function> viPosts = _latentPosts.get(vi);
		if (viPosts == null) {
			viIdx = Arrays.binarySearch(vars, vi);
		}

		Map<DataCase, Function> vjPosts = _latentPosts.get(vj);
		if (vjPosts == null) {
			vjIdx = Arrays.binarySearch(vars, vj);
		}

		Function empDist = Function.createFunction(varPair);

		for (DataCase datum : _data.getData()) {
			int[] states = datum.getStates();

			// If there is missing data, continue;
			if ((viIdx != -1 && states[viIdx] == -1)
					|| (vjIdx != -1 && states[vjIdx] == -1)) {
				continue;
			}
			// P(vi, vj|d) = P(vi|d) * P(vj|d)
			Function freq;

			if (viPosts == null) {
				freq = Function.createIndicatorFunction(vi, states[viIdx]);
			} else {
				freq = viPosts.get(datum);
			}

			if (vjPosts == null) {
				freq = freq.times(Function.createIndicatorFunction(vj, states[vjIdx]));
			} else {
				freq = freq.times(vjPosts.get(datum));
			}

			freq = freq.times(datum.getWeight());

			empDist.plus(freq);
		}

		empDist.normalize();

		return empDist;
	}

	private static Map<Variable, Map<Variable, Double>> processMi(List<Variable> bestPair,
			List<double[]> miArray, List<Variable> vars) {
		// convert the array to map

		// initialize the data structure for pairwise MI
		Map<Variable, Map<Variable, Double>> mis = new HashMap<Variable, Map<Variable, Double>>(
				vars.size());

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		for (int i = 0; i < vars.size(); i++) {
			double[] row = miArray.get(i);

			Map<Variable, Double> map = new HashMap<Variable, Double>(vars.size());
			for (int j = 0; j < vars.size(); j++) {
				map.put(vars.get(j), row[j]);

				// find the best pair
				if (row[j] > maxMi) {
					maxMi = row[j];
					first = vars.get(i);
					second = vars.get(j);
				}
			}

			mis.put(vars.get(i), map);

			// to allow garbage collection
			miArray.set(i, null);
		}

		// set the best pair
		bestPair.add(first);
		bestPair.add(second);

		return mis;

	}

	/**
	 * 
	 * Return the best pair of variables with max MI in _mis.
	 */
	private void findBestPair(ArrayList<Variable> bestPair, Set<Variable> VariablesSet) {
		// Initialize vars as _VarisblesSet
		List<Variable> vars = new ArrayList<Variable>(VariablesSet);

		List<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		int nVars = vars.size();

		// enumerate all pairs of variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				double mi = _mis.get(vi).get(vj);

				// update max MI and indices of best pair
				if (mi > maxMi) {
					maxMi = mi;
					first = vi;
					second = vj;
				}
			}
		}

		// set the best pair
		bestPair.add(first);
		bestPair.add(second);
	}

}
