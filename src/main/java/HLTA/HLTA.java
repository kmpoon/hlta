package HLTA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.UndirectedGraph;
import org.latlab.learner.ParallelEAST_Restricted;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.learner.ParallelLCMlearner;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.latlab.util.DataSetLoader;
import org.latlab.util.Function;
import org.latlab.util.ScoreCalculator;
import org.latlab.util.StringPair;
import org.latlab.util.Utils;
import org.latlab.util.Variable;

/**
 * Latent tree analysis for topic detection.
 * 
 * @author LIU Tengfei
 * 
 */
public class HLTA {

	/**
	 * Main method
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 10) {
			System.err.println("Usage: java HLTA data outDir LocalEmNumRestarts LocalEmMaxSteps FirtEmNumRestarts FirstEmMaxSteps FinalEmNumRestarts FinalEmMaxSteps EM-threshold UDtest-threshold");
			System.exit(1);
		}

		try {
			HLTA learner = new HLTA();
			learner.initialize(args);

			// call main method.
			long start = System.currentTimeMillis();
			learner.learn();
			System.out.println("--- Total Time: " + (System.currentTimeMillis() - start) + " ms ---");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Original data.
	 */
	private DataSet _OriginalData;

	private DataSet _data;

	/**
	 * Threshold for UD-test.
	 */
	private double _UDthreshold;

	/**
	 * Threshold for EM.
	 */
	private double _emThreshold;

	/**
	 * Parameter for First full EM.
	 */
	private int _FirstEmMaxSteps;

	/**
	 * Parameter for First full EM.
	 */
	private int _FirstEmNumRestarts;

	/**
	 * Parameter for Local EM.
	 */
	private int _LocalEmMaxSteps;

	/**
	 * Parameter for Local EM.
	 */
	private int _LocalEmNumRestarts;

	/**
	 * Parameter for final full EM.
	 */
	private int _FinalEmMaxSteps;

	/**
	 * Parameter for final full EM.
	 */
	private int _FinalEmNumRestarts;

	/**
	 * The collection of hierarchies. Each hierarchy represents a LCM and is
	 * indexed by the variable at its root.
	 */
	private Map<Variable, LTM> _hierarchies;

	/**
	 * The collection of manifest variables that wait to do U-test.
	 */
	private Set<Variable> _VariablesSet;

	private Set<Variable> _InternalNodeNeedRemove;

	/**
	 * The collection of posterior distributions P(Y|d) for each latent variable
	 * Y at the root of a hierarchy and each data case d in the training data.
	 */
	private Map<Variable, Map<DataCase, Function>> _latentPosts;

	/**
	 * Output directory.
	 */
	private String _outDir;

	/**
	 * The collection of pairwise mutual information.
	 */
	private Map<Variable, Map<Variable, Double>> _mis;

	/**
	 * whether true class labels are included in data file.
	 */
	boolean _noLabel = false;

	/**
	 * In case that the sibling cluster is large. 
	 */
	int _maxIslandSize = 15;

	/**
	 * Initialize the parameters.
	 * 
	 * @param args
	 * @throws IOException
	 */
	private void initialize(String[] args) throws IOException {

		try {
			_OriginalData = new DataSet(DataSetLoader.convert(args[0]));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		_outDir = args[1];
		_LocalEmNumRestarts = Integer.parseInt(args[2]);
		_LocalEmMaxSteps = Integer.parseInt(args[3]);
		_FirstEmNumRestarts = Integer.parseInt(args[4]);
		_FirstEmMaxSteps = Integer.parseInt(args[5]);
		_FinalEmNumRestarts = Integer.parseInt(args[6]);
		_FinalEmMaxSteps = Integer.parseInt(args[7]);
		_emThreshold = Double.parseDouble(args[8]);
		_UDthreshold = Double.parseDouble(args[9]);

		_InternalNodeNeedRemove = new HashSet<Variable>();
	}

	/**
	 * Learn single island.
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 */
	private void SingleIslandLearner() throws FileNotFoundException,
			UnsupportedEncodingException {
		ArrayList<Variable> bestPair = new ArrayList<Variable>();

		// _mis only needs to compute once
		if (_mis.isEmpty()) {
			// compute MI and find the pair with the largest MI value
			long start = System.currentTimeMillis();
			_mis = computeMis(bestPair);
			System.out.println("--- Total Time for computing MI: " + (System.currentTimeMillis() - start) + " ms ---");

		} else {
			findBestPair(bestPair);
		}

		// the cluster is initialized as the best pair
		Set<Variable> cluster = new HashSet<Variable>(bestPair);

		// learn a sub-model with the best pair
		LTM subModel = null;

		// starting from 2, try to increase the cluster size one by one
		while (cluster.size() < _VariablesSet.size()) 
		{
			if(cluster.size() == _maxIslandSize && cluster.size() < _VariablesSet.size()-1)
				break;
			
			// try to find the closest variable
			ArrayList<Variable> ClosestVariablePair = findShortestOutLink(_mis, cluster);

			if (!ClosestVariablePair.isEmpty()) {
				cluster.addAll(ClosestVariablePair);
			}

			/**
			 * If there are less than 3 manifest variables, the learned LTM will
			 * have only 1 latent node. Otherwise, there will be a redundant
			 * latent node which connects to only one manifest variable. In
			 * other words, if there are less than 3 manifest variables, these
			 * variables are always unidimensional.
			 */
			if (cluster.size() >= 4 || _VariablesSet.size() < 4) {
				/**
				 * This part implements the unidimensionality test.
				 */
				// learn a sub-model with the new cluster
				LTM newSubModel = learnSubModel(cluster, ClosestVariablePair);

				System.out.print("Consider: ");
				for (Variable v : cluster) {
					System.out.print(" " + v.getName());
				}
				System.out.println();

				// check whether the new sub-model is unidimensional.
				if (!IsModelUnidimensional(newSubModel)) {
					// learn a uni_model, although UniModel is never used,
					// _uniModelBIC is updated during the learning.
					LTM UniModel = learnSubModel(cluster, null);

					double mulModelBIC = ScoreCalculator.computeBic(newSubModel, _data);
					double uniModelBIC = ScoreCalculator.computeBic(UniModel, _data);

					if (mulModelBIC - uniModelBIC > _UDthreshold) 
					{
						System.out.println(" Diff : " + (mulModelBIC - uniModelBIC));
						// Learn a 1-LT model on the sibling cluster.
						subModel = learnLCMOnSilblingCluster(newSubModel, bestPair);
						break;
					} else {
						subModel = null;
					}
				} else {
					subModel = newSubModel;
				}
			}
		}

		if (subModel == null) {
			if (cluster.size() == _maxIslandSize)
				System.out.println("Max Island!");

			subModel = learnSubModel(cluster, null);
		}

		// update VariablesSet
		updateVariablesSet(subModel);

		// update hierarchies
		updateHierarchies(subModel);
	}

	/**
	 * Update the collection of hierarchies.
	 */
	private void updateHierarchies(LTM subModel) {
		BeliefNode root = subModel.getRoot();

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
		}
	}

	/**
	 * Find the closest variable to cluster.
	 * 
	 * @param mis
	 * @param cluster
	 * @return
	 */
	private ArrayList<Variable> findShortestOutLink(
			Map<Variable, Map<Variable, Double>> mis, Set<Variable> cluster) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			for (Entry<Variable, Double> entry : mis.get(inCluster).entrySet()) {
				Variable outCluster = entry.getKey();
				double mi = entry.getValue();

				// skip variables already in cluster
				if (cluster.contains(outCluster) || !(_VariablesSet.contains(outCluster))) {
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

		// Set<Variable> ClosestVariablePair = new HashSet<Variable>();
		ArrayList<Variable> ClosestVariablePair = new ArrayList<Variable>();
		ClosestVariablePair.add(bestInCluster);
		ClosestVariablePair.add(bestOutCluster);

		return ClosestVariablePair;
	}

	/**
	 * Compute the empirical distribution of the given pair of variables based
	 * on the posterior distributions P(Y|d) and the training data.
	 */
	private Function computeEmpDist(ArrayList<Variable> varPair) {
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

		DataSet data = null;
		if (viPosts == null && vjPosts == null) {
			data = _data.project(varPair);
			vars = data.getVariables();
			viIdx = Arrays.binarySearch(vars, vi);
			vjIdx = Arrays.binarySearch(vars, vj);
		} else {
			data = _data;
		}

		for (DataCase datum : data.getData()) {
			int[] states = datum.getStates();

			// If there is missing data, continue;
			if (data.getDimension() == varPair.size()) {
				if (states[0] == -1 || states[1] == -1)
					continue;

			} else if ((viIdx != -1 && states[viIdx] == -1)
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
				freq =
						freq.times(Function.createIndicatorFunction(vj,
								states[vjIdx]));
			} else {
				freq = freq.times(vjPosts.get(datum));
			}

			freq = freq.times(datum.getWeight());

			empDist.plus(freq);
		}

		empDist.normalize();

		return empDist;
	}

	/**
	 * Compute pairwise MI between the specified list of variable based on the
	 * posterior distributions P(Y|d) and the training data. Also swap the pair
	 * of variables with the largest MI to the head of the list.
	 */
	private Map<Variable, Map<Variable, Double>> computeMis(
			ArrayList<Variable> bestPair) {
		// initialize the data structure for pairwise MI
		Map<Variable, Map<Variable, Double>> mis =
				new HashMap<Variable, Map<Variable, Double>>();
		List<Variable> vars = new ArrayList<Variable>(_VariablesSet);

		for (Variable var : vars) {
			mis.put(var, new HashMap<Variable, Double>());
		}

		ArrayList<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		int nVars = vars.size();

		// normalize the MI or not
		boolean normalize = false;

		// enumerate all pairs of variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair);
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

				// keep both I(vi; vj) and I(vj; vi)
				mis.get(vi).put(vj, mi);
				mis.get(vj).put(vi, mi);

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

		return mis;
	}

	/*
	 * Return the best pair of variables with max MI in _mis.
	 */
	private void findBestPair(ArrayList<Variable> bestPair) {
		// Initialize vars as _VarisblesSet
		List<Variable> vars = new ArrayList<Variable>(_VariablesSet);

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

	/**
	 * Return true if and only if the given new sub-model is
	 * unidimensional(contains only one latent variable).
	 */
	private boolean IsModelUnidimensional(LTM newSubModel) {
		Set<Variable> latents = newSubModel.getLatVars();
		return latents.size() == 1;
	}

	/**
	 * Initialization.
	 */
	private void initialize(DataSet data) {
		System.out.println("=== Initialization ===");

		// initialize data structures for P(Y|d)
		_latentPosts = new HashMap<Variable, Map<DataCase, Function>>();

		// initialize hierarchies
		// _hirearchies will be used to keep all LCMs found by U-test.
		_hierarchies = new HashMap<Variable, LTM>();

		_VariablesSet = new HashSet<Variable>();

		_mis = new HashMap<Variable, Map<Variable, Double>>();

		_data = data;

		// add all manifest variable to variable set _VariableSet.
		for (Variable var : _data.getVariables()) {
			_VariablesSet.add(var);
		}
	}

	/**
	 * Return true if and only if the whole clustering procedure is done, or
	 * equivalently, there is only one hierarchy left.
	 */
	private boolean isDone() {
		return _VariablesSet.size() < 1;
	}

	public BayesNet learn() throws FileNotFoundException, UnsupportedEncodingException 
	{
		DataSet data = _OriginalData;
		BayesNet CurrentModel = null;

		int level = 2;
		while (true) {
			BayesNet refineTree = FlatBI(data, level);

			CurrentModel = BuildHierarchy(CurrentModel, refineTree);

			if (CurrentModel != null)
				CurrentModel.saveAsBif(_outDir + File.separator + "Hierarchical-LTM-Level-" + (level - 1) + ".bif");

			if (refineTree.getInternalVars().size() <= 2)
				break;

			data = HardAssignment(CurrentModel, refineTree);

			level++;
		}
		
		//rename latent variables, reorder the states.
		CurrentModel = postProcessingModel(CurrentModel);
		
		//output final model.
		CurrentModel.saveAsBif(_outDir + File.separator + "Hierarchical-LTM-final.bif");

		double score = ScoreCalculator.computeBic(CurrentModel, _OriginalData);
		System.out.println("BIC Score on the training data: " + score);

		return CurrentModel;
	}
	
	private BayesNet postProcessingModel(BayesNet model)
	{
		HashMap<Integer, HashSet<String>> varDiffLevels = processVariables(model);
		HashMap<Integer, Integer> levelAndIndex = new HashMap<Integer, Integer>();
		
		//reorderStates first.
		model = reorderStates(model,varDiffLevels);
		
		int topLevelIndex = varDiffLevels.size()-1;
		
		for(int i=1; i<topLevelIndex+1; i++)
		{
			levelAndIndex.put(i, 0);
		}
		
		HashSet<String> topLevel = varDiffLevels.get(topLevelIndex);
		
		//renameVariables
		for(String str : topLevel)
		{
			processName(model, str, topLevelIndex, levelAndIndex, varDiffLevels);
		}
				
		return model;
	}
	
	private BayesNet reorderStates(BayesNet bn, HashMap<Integer, HashSet<String>> varDiffLevels)
	{
		// inference engine
		CliqueTreePropagation ctp = new CliqueTreePropagation(bn);

		Variable[] latents = new Variable[1];
		int[] states = new int[1];

		// reorder states for each latent variable
		for (Variable latent : bn.getInternalVars()) {
			latents[0] = latent;

			// calculate severity of each state
			int card = latent.getCardinality();
			double[] severity = new double[card];

			for (int i = 0; i < card; i++) {
				states[0] = i;

				ctp.setEvidence(latents, states);
				ctp.propagate();

				// accumulate expectation of each manifest variable
				int VarLevel = 0;
				for(int key : varDiffLevels.keySet())
				{
					HashSet<String> set = varDiffLevels.get(key);
					if(set.contains(latent.getName())){
						VarLevel = key;
						break;
					}
				}
				
				Set<DirectedNode> setNode = new HashSet<DirectedNode>();
				Set<DirectedNode> childSet = bn.getNode(latent).getChildren();
				for(DirectedNode node : childSet){
					Variable var = ((BeliefNode)node).getVariable();
					
					if(!varDiffLevels.get(VarLevel-1).contains(var.getName()))
						continue;
					
					if(((BeliefNode)node).isLeaf()){
						setNode.add((DirectedNode) node);	
					}else{
						for(AbstractNode nodeDes : bn.getNode(var).getDescendants())
						{
							if(((BeliefNode)nodeDes).isLeaf())
							{
								setNode.add((DirectedNode) nodeDes);
							}
						}
					}
				}
				
				for (DirectedNode node : setNode) {
					Variable manifest = ((BeliefNode)node).getVariable();
					double[] dist = ctp.computeBelief(manifest).getCells();

					for (int j = 0; j < dist.length; j++) {
							severity[i] += j * dist[j];
					}
				}
			}

			// initial order
			int[] order = new int[card];
			for (int i = 0; i < card; i++) {
				order[i] = i;
			}

			// bubble sort
			for (int i = 0; i < card - 1; i++) {
				for (int j = i + 1; j < card; j++) {
					if (severity[i] > severity[j]) {
						int tmpInt = order[i];
						order[i] = order[j];
						order[j] = tmpInt;

						double tmpReal = severity[i];
						severity[i] = severity[j];
						severity[j] = tmpReal;
					}
				}
			}

			// reorder states
			bn.getNode(latent).reorderStates(order);
			latent.standardizeStates();
		}
		
		return bn;
	}
	
	private void processName(BayesNet model, String str, int level, HashMap<Integer, Integer> levelAndIndex, HashMap<Integer, HashSet<String>> varDiffLevels)
	{
		if(varDiffLevels.get(0).contains(str))
			return;
		
		Set<DirectedNode> set = model.getNodeByName(str).getChildren();
		
		changeName(model, str, level, levelAndIndex);
		
		for(DirectedNode child : set)
		{		
			if(!varDiffLevels.get(level-1).contains(child.getName()))
				continue;
			
			processName(model, child.getName(), level-1, levelAndIndex, varDiffLevels);
		}
	}
	
	private void changeName(BayesNet model, String str, int level, HashMap<Integer, Integer> levelAndIndex)
	{
		BeliefNode var = model.getNodeByName(str);
		
		int index = levelAndIndex.get(level)+1;
		String newName = "Z"+level+index;
		var.setName(newName);
		
		levelAndIndex.put(level, index);
	}
	
	private HashMap<Integer, HashSet<String>> processVariables(BayesNet model)
	{
		HashMap<Integer, HashSet<String>> varDiffLevels = new HashMap<Integer, HashSet<String>>();
		
		Set<Variable> internalVar = model.getInternalVars();
		Set<Variable> leafVar     = model.getLeafVars();
		
		
		HashSet<String> levelOne = new HashSet<String>();
		for(Variable v : leafVar)
		{
			levelOne.add(v.getName());
		}
		varDiffLevels.put(0, levelOne);
		
		int level=0;
		while(internalVar.size()>0)
		{
			HashSet<String> levelVar = varDiffLevels.get(level);
			level++;
			
			HashSet<String> newSet = new HashSet<String>();
			for(String s : levelVar)
			{
				String parent = model.getNodeByName(s).getParent().getName();
				
				if(parent != null)
				{
					internalVar.remove(model.getNodeByName(parent).getVariable());
					newSet.add(parent);
				}
			}
			varDiffLevels.put(level, newSet);
		}
		
		return varDiffLevels;
	}

	private BayesNet BuildHierarchy(BayesNet CurrentModel, BayesNet tree) {
		if (CurrentModel == null) {
			CurrentModel = tree;
			return CurrentModel;
		}

		Set<Edge> edgeSet = new HashSet<Edge>();

		for (Edge e : CurrentModel.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			if (tree.getNode(head) != null && tree.getNode(tail) != null) {
				edgeSet.add(e);
			}
		}

		for (Variable var : _InternalNodeNeedRemove) {
			CurrentModel.removeNode(CurrentModel.getNode(var.getName()));
		}

		if (_InternalNodeNeedRemove.size() > 0)
			_InternalNodeNeedRemove.clear();

		for (Edge e : edgeSet) {
			CurrentModel.removeEdge(e);
		}

		for (Variable v : tree.getInternalVars()) {
			CurrentModel.addNode(v);
		}

		for (Edge e : tree.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			CurrentModel.addEdge(CurrentModel.getNodeByName(head),
					CurrentModel.getNodeByName(tail));
		}

		HashSet<String> donotUpdate = new HashSet<String>();
		for (AbstractNode node : CurrentModel.getNodes()) {
			if (tree.getNode(node.getName()) == null) {
				donotUpdate.add(node.getName());
			}
		}

//		 EmLearner FinalemLearner = new EmLearner();
//		 FinalemLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
//		 FinalemLearner.setMaxNumberOfSteps(_FinalEmMaxSteps);
//		 FinalemLearner.setNumberOfRestarts(_FinalEmNumRestarts);
//		 FinalemLearner.setReuseFlag(false);
//		 FinalemLearner.setThreshold(_emThreshold);
//		 FinalemLearner.setDontUpdateNodes(donotUpdate);

		ParallelEmLearner FinalemLearner = new ParallelEmLearner();
		FinalemLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		FinalemLearner.setMaxNumberOfSteps(_FinalEmMaxSteps);
		FinalemLearner.setNumberOfRestarts(_FinalEmNumRestarts);
		FinalemLearner.setReuseFlag(false);
		FinalemLearner.setThreshold(_emThreshold);
		FinalemLearner.setDontUpdateNodes(donotUpdate);

		long start = System.currentTimeMillis();
		CurrentModel = FinalemLearner.em(CurrentModel, _OriginalData);
		System.out.println("--- Total Time for build hierarchicy (EM): " + (System.currentTimeMillis() - start) + " ms ---");

		return CurrentModel;
	}

	private BayesNet FlatBI(DataSet data, int level)
			throws FileNotFoundException, UnsupportedEncodingException {
		int i = 1;

		initialize(data);

		while (!isDone()) {
			System.out.println("============= Level :" + level + ", Learn Island : " + i + " , number of variables left: " + _VariablesSet.size() + "  =================");

			SingleIslandLearner();

			i++;
		}

		LTM latentTree = BuildLatentTree();

		BayesNet refineTree = refine(latentTree);

		return refineTree;
	}

	private LTM BuildLatentTree() {
		if (_latentPosts.isEmpty()) {
			for (Variable var : _hierarchies.keySet()) {
				LTM subModel = _hierarchies.get(var);
				updateStats(subModel);
			}
		}

		LTM latentTree = new LTM();

		// Construct tree: first, add all manifest nodes and latent nodes.
		// Second, copy the edges and CPTs in each LCMs.
		for (Variable var : _hierarchies.keySet()) {
			LTM tempTree = _hierarchies.get(var);

			for (AbstractNode node : tempTree.getNodes()) {
				latentTree.addNode(((BeliefNode) node).getVariable());
			}

			// copy the edges and CPTs
			for (AbstractNode node : tempTree.getNodes()) {
				BeliefNode bNode = (BeliefNode) node;

				if (!bNode.isRoot()) {
					BeliefNode parent = (BeliefNode) bNode.getParent();

					BeliefNode newNode =
							latentTree.getNode(bNode.getVariable());
					BeliefNode newParent =
							latentTree.getNode(parent.getVariable());

					latentTree.addEdge(newNode, newParent);
					newNode.setCpt(bNode.getCpt().clone()); // copy the parameters of manifest variables
				}
			}
		}

		UndirectedGraph mst = learnMaximumSpanningTree(_hierarchies);

		Queue<AbstractNode> frontier = new LinkedList<AbstractNode>();
		frontier.offer(mst.getNodes().peek());

		// add the edges among latent nodes.
		while (!frontier.isEmpty()) {
			AbstractNode node = frontier.poll();
			DirectedNode dNode =
					(DirectedNode) latentTree.getNode(node.getName());

			for (AbstractNode neighbor : node.getNeighbors()) {
				DirectedNode dNeighbor =
						(DirectedNode) latentTree.getNode(neighbor.getName());
				if (!dNode.hasParent(dNeighbor)) {
					latentTree.addEdge(dNeighbor, dNode);
					frontier.offer(neighbor);
				}
			}
		}

		ArrayList<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		// //Configure the parameters of latent nodes
		for (Variable var : latentTree.getLatVars()) {
			BeliefNode parentNode =
					(BeliefNode) latentTree.getNode(var).getParent();
			if (parentNode != null) {
				varPair.set(0, var);
				varPair.set(1, parentNode.getVariable());

				Function cpt = computeEmpDist(varPair);
				cpt.normalize(var);

				latentTree.getNode(var).setCpt(cpt);
			} else {
				Function cpt = _hierarchies.get(var).getRoot().getCpt().clone();
				latentTree.getNode(var).setCpt(cpt);
			}
		}

		return latentTree;
	}

	/**
	 * Re-consider the cardinality and then refine the parameters of the final
	 * model using full EM.
	 */
	private BayesNet refine(LTM model) throws UnsupportedEncodingException,
			FileNotFoundException {
//		 System.out.println("Optimize the model before refine the states.");
//		 EmLearner emLearner = new EmLearner();
//		 emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
//		 emLearner.setMaxNumberOfSteps(_FirstEmMaxSteps);
//		 emLearner.setNumberOfRestarts(_FirstEmNumRestarts);
//		 emLearner.setReuseFlag(true);
//		 emLearner.setThreshold(_emThreshold);

//		model.saveAsBif(_outDir + File.separator + "before-refine" + ".bif");

		System.out.println("Optimize the model before refine the states.");
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_FirstEmMaxSteps);
		emLearner.setNumberOfRestarts(_FirstEmNumRestarts);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(_emThreshold);

		if (model.getLeafVars().size() != _data.getDimension())
			_data = _data.synchronize(model);

		long start = System.currentTimeMillis();
		model = (LTM) emLearner.em(model, _data);
		System.out.println("---First EM(P) Time: " + (System.currentTimeMillis() - start) + " ms ---");
		System.out.println();

		// for each manifest variable, find its nearest latent node.
		Map<Variable, Variable> nodeNeedRelocate = FindNodeNeedRelocate(model);

		// Re-consider the structure and cardinality of latent nodes.
		LTM model_refined = reconsiderStructure(model, _data, nodeNeedRelocate);

		// System.out.println("Optimize the model before refine the states.");
		ParallelEmLearner FinalemLearner = new ParallelEmLearner();
//		EmLearner FinalemLearner = new EmLearner();
		FinalemLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		FinalemLearner.setMaxNumberOfSteps(_FinalEmMaxSteps);
		FinalemLearner.setNumberOfRestarts(_FinalEmNumRestarts);
		FinalemLearner.setReuseFlag(false);
		FinalemLearner.setThreshold(_emThreshold);

		// LTM model_final = null;

		// LTM model_clone = model_refined.clone();
		// model_clone = (LTM) FinalemLearner.em(model_clone, _data);

		// double score = ScoreCalculator.computeBic(CurrentModel,
		// _OriginalData);
		// System.out.println("BIC Score on the training data (LTM EM): " +
		// model_clone.getBICScore(_data));

		BayesNet model_BN = Ltm2Bn(model_refined);
		_data = _data.synchronize(model_BN);

		start = System.currentTimeMillis();
		model_BN = FinalemLearner.em(model_BN, _data);
		System.out.println("---Second EM Time: " + (System.currentTimeMillis() - start) + " ms ---");
		System.out.println();
		
		System.out.println("BIC Score on the training data (BN EM): " + model_BN.getBICScore(_data));
		return model_BN;
	}

	private BayesNet Ltm2Bn(LTM model) {
		BayesNet copy = new BayesNet(model.getName());

		// copies nodes
		for (AbstractNode node : model.getNodes()) {
			copy.addNode(((BeliefNode) node).getVariable());
		}

		// copies edges
		for (Edge edge : model.getEdges()) {
			copy.addEdge(copy.getNode(edge.getHead().getName()),
					copy.getNode(edge.getTail().getName()));
		}
		// copies CPTs
		for (AbstractNode node : copy.getNodes()) {
			BeliefNode bNode = (BeliefNode) node;
			bNode.setCpt(copy.getNode(bNode.getVariable()).getCpt().clone());
		}

		return copy;
	}

	private Map<Variable, Variable> FindNodeNeedRelocate(LTM model) {
		System.out.println("--- Update statistics for all latent varaibles ---");

		long start = System.currentTimeMillis();

		_latentPosts.clear();

		Map<Variable, Integer> varIdx = _data.createVariableToIndexMap();

		Variable[] vars = model.getManifestVars().toArray(new Variable[0]);
		int nvars = vars.length;
		int[] subStates = new int[nvars];

		for (Variable latent : model.getLatVars()) {

			Map<DataCase, Function> latentPosts =
					new HashMap<DataCase, Function>();
			CliqueTreePropagation ctp = new CliqueTreePropagation(model);

			// update for every data case
			for (DataCase dataCase : _data.getData()) {
				// project states
				int[] states = dataCase.getStates();
				for (int i = 0; i < nvars; i++) {
					subStates[i] = states[varIdx.get(vars[i])];
				}

				// set evidence and propagate
				ctp.setEvidence(vars, subStates);
				ctp.propagate();

				// compute P(Y|d)
				Function post = ctp.computeBelief(latent);
				latentPosts.put(dataCase, post);
			}

			_latentPosts.put(latent, latentPosts);
		}

		ArrayList<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		Map<Variable, Variable> nodeNeedRelocate =
				new HashMap<Variable, Variable>();

		// enumerate all pairs of variables
		for (int i = 0; i < nvars; i++) {
			Variable vi = vars[i];
			varPair.set(0, vi);

			double maxMi = Double.NEGATIVE_INFINITY;
			Variable closestLatent = null;

			for (Variable vj : model.getLatVars()) {
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair);
				double mi = Utils.computeMutualInformation(pairDist);

				// update max MI and indices of best pair
				if (mi > maxMi) {
					maxMi = mi;
					closestLatent = vj;
				}
			}

			if (!((BeliefNode) model.getNode(vi).getParent()).getVariable().equals(
					closestLatent)) {
				nodeNeedRelocate.put(vi, closestLatent);
			}
		}

		System.out.println("--- Elapsed Time: "
				+ (System.currentTimeMillis() - start) + " ms ---");
		System.out.println();

		return nodeNeedRelocate;
	}

	private UndirectedGraph learnMaximumSpanningTree(
			Map<Variable, LTM> hierarchies) {
		// initialize the data structure for pairwise MI
		ArrayList<StringPair> pairs = new ArrayList<StringPair>();

		// the collection of latent variables.
		List<Variable> vars = new ArrayList<Variable>(hierarchies.keySet());

		ArrayList<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		int nVars = vars.size();

		// enumerate all pairs of latent variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair);
				double mi = Utils.computeMutualInformation(pairDist);

				// keep both I(vi; vj) and I(vj; vi)
				pairs.add(new StringPair(vi.getName(), vj.getName(), mi));
			}
		}

		// sort the pairwise MI.
		Collections.sort(pairs);

		// building MST using Kruskal's algorithm
		UndirectedGraph mst = new UndirectedGraph();

		// nVars = latentTree.getNumberOfNodes();
		HashMap<String, ArrayList<String>> components =
				new HashMap<String, ArrayList<String>>();

		for (Variable var : hierarchies.keySet()) {
			String name = var.getName();
			mst.addNode(name);

			ArrayList<String> component = new ArrayList<String>(nVars);
			component.add(name);
			components.put(name, component);
		}

		// examine pairs in descending order w.r.t. MI
		for (int i = pairs.size() - 1; i >= 0; i--) {
			StringPair pair = pairs.get(i);
			String a = pair.GetStringA();
			String b = pair.GetStringB();
			ArrayList<String> aComponent = components.get(a);
			ArrayList<String> bComponent = components.get(b);

			// check whether a and b are in the same connected component
			if (aComponent != bComponent) {
				// connect nodes
				mst.addEdge(mst.getNode(a), mst.getNode(b));

				if (aComponent.size() + bComponent.size() == nVars) {
					// early termination: the tree is done
					break;
				}

				// merge connected component
				aComponent.addAll(bComponent);
				for (String c : bComponent) {
					components.put(c, aComponent);
				}
			}
		}

		return mst;
	}

	private DataSet HardAssignment(BayesNet CurrentModel, BayesNet latentTree) {
		ArrayList<DataCase> data = _OriginalData.getData();

		Variable[] varName = new Variable[latentTree.getInternalVars().size()];
		int[][] newData = new int[data.size()][varName.length];

		CliqueTreePropagation ctp = new CliqueTreePropagation(CurrentModel);

		int index = 0;
		for (Variable latent : latentTree.getInternalVars()) {
			Variable clone =
					CurrentModel.getNodeByName(latent.getName()).getVariable();
			varName[index] = new Variable(clone.getName(), clone.getStates());
			index++;
		}

		// update for every data case
		for (int j = 0; j < data.size(); j++) {
			DataCase dataCase = data.get(j);

			int[] states = dataCase.getStates();

			// set evidence and propagate
			ctp.setEvidence(_OriginalData.getVariables(), states);
			ctp.propagate();

			for (int i = 0; i < varName.length; i++) {
				Variable latent =
						((BeliefNode) CurrentModel.getNode(varName[i].getName())).getVariable();

				// compute P(Y|d)
				Function post = ctp.computeBelief(latent);

				double cell = 0;
				int assign = 0;

				for (int k = 0; k < post.getDomainSize(); k++) {
					if (post.getCells()[k] > cell) {
						cell = post.getCells()[k];
						assign = k;
					}
				}

				newData[j][i] = assign;
			}
		}

		DataSet da = new DataSet(varName);
		for (int j = 0; j < data.size(); j++) {
			da.addDataCase(newData[j], data.get(j).getWeight());
		}

		ArrayList<Variable> set = new ArrayList<Variable>();
		for (Variable latent : varName) {
			BeliefNode node = latentTree.getNodeByName(latent.getName());

			int leafChild = 0;
			for (DirectedNode child : node.getChildren()) {
				if (child.isLeaf())
					leafChild += 1;

				if (leafChild > 1) {
					set.add(latent);
					break;
				}
			}
		}

		if (set.size() != varName.length) {
			for (Variable var : varName) {
				if (!set.contains(var)) {
					_InternalNodeNeedRemove.add(var);
				}
			}
			da = da.project(set);
		}

		return da;
	}

	/**
	 * Learn a latent tree with the specified collection of variables as
	 * "manifest variables".
	 */

	private LTM learnSubModel(Set<Variable> Vars, ArrayList<Variable> ClosestVariablePair) throws FileNotFoundException, UnsupportedEncodingException 
	{
		LTM subModel = null;

		// project the data to the specified collection of variables.
		DataSet data = _data.project(new ArrayList<Variable>(Vars));

		if (ClosestVariablePair == null) 
		{
			ParallelLCMlearner learner = new ParallelLCMlearner();
			learner.setEMSettings(_FirstEmNumRestarts, _FirstEmMaxSteps, _emThreshold);
			learner.setBICThreshold(0.1);

			// learn the latent tree.
			subModel = learner.search(data);

		} else {
			// use a modified version of EAST to learn the latent tree.
			ParallelEAST_Restricted learner = new ParallelEAST_Restricted();

			int[] localEMSettings = new int[] { _LocalEmNumRestarts, 0, _LocalEmMaxSteps };
			learner.setLEMSettings("ChickeringHeckerman", localEMSettings, _emThreshold);
			learner.setEMSettings(_FirstEmNumRestarts, _FirstEmMaxSteps, _emThreshold);
			learner.setUseIR(true);
			learner.setClosestVariablePair(ClosestVariablePair);

			// learn the latent tree.
			subModel = learner.search(data);
		}

		return subModel;
	}

	private LTM learnLCMOnSilblingCluster(LTM newSubModel,
			ArrayList<Variable> bestPair) throws FileNotFoundException,
			UnsupportedEncodingException {
		Variable first = bestPair.get(0);
		Variable second = bestPair.get(1);

		BeliefNode firstPar =
				(BeliefNode) newSubModel.getNode(first).getParent();
		BeliefNode secondPar =
				(BeliefNode) newSubModel.getNode(second).getParent();

		int firstSize = 0;
		int secondSize = 0;

		BeliefNode parent = null;

		if (firstPar.equals(secondPar)) {
			parent = firstPar;
		} else {
			if (firstPar.isRoot()) {
				firstSize = firstPar.getChildren().size() - 1;
				secondSize = secondPar.getChildren().size();
			} else {
				firstSize = firstPar.getChildren().size();
				secondSize = secondPar.getChildren().size() - 1;
			}

			if (firstSize > secondSize) {
				parent = firstPar;
			} else if (firstSize == secondSize) {
				double miFirst = 0, miSecond = 0;

				miFirst = computePairwiseMI(newSubModel, firstPar);
				miSecond = computePairwiseMI(newSubModel, secondPar);

				parent = miFirst >= miSecond ? firstPar : secondPar;

			} else {
				parent = secondPar;
			}
		}

		Set<Variable> silblingCluster = new HashSet<Variable>();

		for (DirectedNode child : parent.getChildren()) {
			if (child.isLeaf()) {
				silblingCluster.add(((BeliefNode) child).getVariable());
			}
		}

		LTM model = learnSubModel(silblingCluster, null);

		System.out.print("silbling: ");
		for (Variable v : silblingCluster) {
			System.out.print(" " + v.getName());
		}
		System.out.println();

		return model;
	}

	private double computePairwiseMI(LTM model, BeliefNode node) {
		int size =
				node.isRoot() ? node.getChildren().size() - 1
						: node.getChildren().size();

		DirectedNode[] array = new DirectedNode[size];

		int index = 0;
		for (DirectedNode child : node.getChildren()) {
			if (child.isLeaf()) {
				array[index++] = child;
			}
		}

		double MI = 0;
		for (int i = 0; i < array.length; i++) {
			DirectedNode n1 = array[i];
			Variable v1 = ((BeliefNode) n1).getVariable();

			for (int j = i + 1; j < array.length; j++) {
				DirectedNode n2 = array[j];
				Variable v2 = ((BeliefNode) n2).getVariable();

				MI += _mis.get(v1).get(v2);
			}
		}

		return MI;
	}

	/**
	 * Update the collections of P(Y|d). Specifically, remove the entries for
	 * all the latent variables in the given sub-model except the root, and
	 * compute P(Y|d) for the latent variable at the root and each data case d.
	 */
	private void updateStats(LTM subModel) {
		BeliefNode root = subModel.getRoot();
		Variable latent = root.getVariable();
		// Function prior = root.getCpt();

		for (DirectedNode child : root.getChildren()) {
			_latentPosts.remove(((BeliefNode) child).getVariable());
		}

		Map<DataCase, Function> latentPosts = new HashMap<DataCase, Function>();

		CliqueTreePropagation ctp = new CliqueTreePropagation(subModel);

		Map<Variable, Integer> varIdx = _data.createVariableToIndexMap();

		Variable[] subVars =
				subModel.getManifestVars().toArray(new Variable[0]);
		int nSubVars = subVars.length;
		int[] subStates = new int[nSubVars];

		// update for every data case
		for (DataCase dataCase : _data.getData()) {
			// project states
			int[] states = dataCase.getStates();
			for (int i = 0; i < nSubVars; i++) {
				subStates[i] = states[varIdx.get(subVars[i])];
			}

			// set evidence and propagate
			ctp.setEvidence(subVars, subStates);
			ctp.propagate();

			// compute P(Y|d)
			Function post = ctp.computeBelief(latent);
			latentPosts.put(dataCase, post);
		}

		_latentPosts.put(latent, latentPosts);
	}

	/**
	 * Re-consider the structure of model, for each manifest variable node,
	 * connect it to its nearest latent node.
	 * 
	 * @param model
	 * @param data
	 * @return
	 */

	public LTM reconsiderStructure(LTM model, DataSet data,
			Map<Variable, Variable> map) {
		System.out.println("Begin to do structure adjustment! This may take a while, please wait....");

		HashSet<Variable> parentsWithSingleChild = new HashSet<Variable>();

		for (Variable manifest : map.keySet()) {
			Variable parent =
					((BeliefNode) (model.getNode(manifest).getParent())).getVariable();
			Variable nearestLatentVar = map.get(manifest);

			if (model.getNode(nearestLatentVar) == null) // it is possible that the nearestLatentVar is already deleted.
			{
				continue;
			}

			model.removeEdge(model.getNode(manifest).getEdge(
					model.getNode(parent)));
			model.addEdge(model.getNode(manifest),
					model.getNode(nearestLatentVar));

			// if parent becomes leaf node, delete it;
			// if parent node only has one child left, attach child to parent
			// node of parent and delete parent node.
			if (model.getNode(parent).getNeighbors().size() <= 2) {
				BeliefNode parentParent =
						(BeliefNode) model.getNode(parent).getParent();

				if (parentParent == null) {// it is root node
					if (model.getNode(parent).getChildren().size() == 1) 
					{// only one latent child. delete it directly.
						model.removeNode(model.getNode(parent));
					} else {// one latent child, one manifest child.
						BeliefNode manifestChild = null;
						BeliefNode latentChild = null;

						for (DirectedNode node : model.getNode(parent).getChildren()) {
							if (((BeliefNode) node).isLeaf()) {
								manifestChild = (BeliefNode) node;
							} else {
								latentChild = (BeliefNode) node;
							}
						}

						model.removeEdge(model.getNode(
								manifestChild.getVariable()).getEdge(
								model.getNode(parent)));
						model.addEdge(
								model.getNode(manifestChild.getVariable()),
								model.getNode(latentChild.getVariable()));
						model.removeNode(model.getNode(parent));
					}

				} else {// it is not root, it has one parent, one child
					BeliefNode child =
							(BeliefNode) model.getNode(parent).getChildren().iterator().next();

					model.removeEdge(model.getNode(parent).getEdge(child));
					model.addEdge(child, parentParent);
					model.removeNode(model.getNode(parent));
				}
			}

			// detect latent variable with single leaf child.
			int numLeafChild = 0;
			if (model.getNode(parent) != null) {
				for (DirectedNode child : model.getNode(parent).getChildren()) {
					if (child.isLeaf())
						numLeafChild++;

					if (numLeafChild > 1)
						break;
				}
			}

			if (numLeafChild == 1) {
				parentsWithSingleChild.add(parent);
			}

			System.out.println("Relocate manifest variable: "
					+ manifest.getName() + " from " + parent.getName() + " to "
					+ nearestLatentVar.getName());
		}

		for (Variable parent : parentsWithSingleChild) {
			// 1. parent is already deleted.
			if (model.getNode(parent) == null)
				continue;

			// 2. parent has more than one leaf child now. Or has no leaf child.
			int numLeafChild = 0;
			Variable SingleChild = null;
			for (DirectedNode child : model.getNode(parent).getChildren()) {
				if (child.isLeaf()) {
					numLeafChild++;
					SingleChild = ((BeliefNode) child).getVariable();
				}
			}

			if (numLeafChild > 1 || numLeafChild == 0)
				continue;

			// 3. relocate to nearest island.
			Variable relocateTo = findClosestLatent(model, SingleChild);

			model.removeEdge(model.getNode(SingleChild).getEdge(
					model.getNode(parent)));
			model.addEdge(model.getNode(SingleChild), model.getNode(relocateTo));

			System.out.println("Relocate single child variable: "
					+ SingleChild.getName() + " from " + parent.getName()
					+ " to " + relocateTo.getName());

			// the parent may need deletion.
			if (model.getNode(parent).getNeighbors().size() > 2) {
				continue;
			} else {
				// 1. if parent is root with two latent children...
				if (model.getNode(parent).getParent() == null) {
					BeliefNode[] children = new BeliefNode[2];
					int index = 0;

					for (DirectedNode node : model.getNode(parent).getChildren()) {
						children[index++] = (BeliefNode) node;
					}

					model.removeEdge(children[0].getEdge(model.getNode(parent)));
					model.removeEdge(children[1].getEdge(model.getNode(parent)));
					model.addEdge(children[0], children[1]);
					model.removeNode(model.getNode(parent));
				} else {// 2. if parent is not a root...
					BeliefNode p =
							(BeliefNode) (model.getNode(parent).getParent());
					BeliefNode c =
							(BeliefNode) model.getNode(parent).getChildren().iterator().next();

					model.removeEdge(p.getEdge(model.getNode(parent)));
					model.removeEdge(c.getEdge(model.getNode(parent)));
					model.addEdge(c, p);
					model.removeNode(model.getNode(parent));
				}
			}
		}

		System.out.println("End of structure adjustment!");

		return model;
	}

	private Variable findClosestLatent(BayesNet tree, Variable var) {
		ArrayList<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable closestLatent = null;

		for (Variable latent : tree.getInternalVars()) {
			if (tree.getNode(var).getParent().getName().equalsIgnoreCase(
					latent.getName()))
				continue;

			int numLeafChild = 0;
			for (DirectedNode child : tree.getNode(latent).getChildren()) {
				if (child.isLeaf()) {
					numLeafChild++;
				}
			}

			if (numLeafChild == 0)
				continue;

			varPair.set(0, tree.getNodeByName(var.getName()).getVariable());
			varPair.set(1, latent);

			Function pairDist = computeEmpDist(varPair);
			double mi = Utils.computeMutualInformation(pairDist);

			// update max MI and indices of best pair
			if (mi > maxMi) {
				maxMi = mi;
				closestLatent = latent;
			}
		}

		return closestLatent;
	}

	public BayesNet learnLatentTreeModel(DataSet train, String output,
			int LocalEmNumRestarts, int LocalEmMaxSteps,
			int FirstEmNumRestarts, int FirstEmMaxSteps,
			int FinalEmNumRestarts, int FinalEmMaxSteps, double EmThreshold,
			double UDThreshold) {
		_OriginalData = train;
		_outDir = output;
		_LocalEmNumRestarts = LocalEmNumRestarts;
		_LocalEmMaxSteps = LocalEmMaxSteps;
		_FirstEmNumRestarts = FirstEmNumRestarts;
		_FirstEmMaxSteps = FirstEmMaxSteps;
		_FinalEmNumRestarts = FinalEmNumRestarts;
		_FinalEmMaxSteps = FinalEmMaxSteps;
		_emThreshold = EmThreshold;
		_UDthreshold = UDThreshold;

		_InternalNodeNeedRemove = new HashSet<Variable>();

		BayesNet model = null;

		long start = System.currentTimeMillis();
		try {
			model = learn();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("--- Total Time for learning the model: "
				+ ((System.currentTimeMillis() - start) / 1000) + " s ---\n");

		return model;
	}

}



