package clustering;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.UndirectedGraph;
import org.latlab.learner.ParallelEmLearner;
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

import clustering.EmpiricalMiComputerForBinaryData;


public class StochasticPEM {

	/**
	 * @param args
	 */
	/**
	 * The collection of pairwise mutual information.
	 */

	// private LTM _model;
	private BayesNet _modelEst;

	/**
	 * Original data.
	 */
	private static DataSet _Origdata;

	private DataSet _workingData;

	/**
	 * Threshold for UD-test.
	 */
	private double _UDthreshold;

	/**
	 * Threshold for EM.
	 */
	private double _emThreshold;

	/**
	 * Parameter for EM.
	 */
	private int _EmMaxSteps;

	/**
	 * Parameter for EM.
	 */
	private int _EmNumRestarts;
	/**
	 * The maximum number of latent variables at top level
	 */
	private int _maxTop;
	/**
	 * The collection of hierarchies. Each hierarchy represents a LCM and is
	 * indexed by the variable at its root.
	 */
	private Map<Variable, LTM> _hierarchies;


	/**
	 * The collection of manifest variables that wait to do UD-test.
	 */
	private Set<Variable> _VariablesSet;

	/**
	 * The ArrayList of manifest variables with orders.
	 */
	private ArrayList<Variable> _Variables;
	
	/**
	 * The collection of posterior distributions P(Y|d) for each latent variable
	 * Y at the root of a hierarchy and each data case d in the training data.
	 * USELESS in PEM version But keep it for future use if we need soft
	 * assignment or to keep record
	 */
	private Map<Variable, Map<DataCase, Function>> _latentPosts;


	/**
	 * Maximum number of island size
	 */
	private int _maxIsland;
	/**
	 * The collection of pairwise mutual information.
	 */
	private ArrayList<double[]> _mis;
	/**
	 * whether true class labels are included in data file.
	 */
	boolean _noLabel = false;
	/**
	 * Save bestPair of observed variables for every latent variable(LCM)
	 */
	Map<String, ArrayList<Variable>> _bestpairs =
			new HashMap<String, ArrayList<Variable>>();

	/**
	 * new data for update and hard assignment
	 */
	int[][] _newdata;
	/**
	 * 
	 * The reference node for children of every latent variable
	 * 
	 */
	Map<String, String> _refNodes = new HashMap<String, String>();

	/**
	 * Name the model you get
	 */
	String _modelname;
	/**
     * Store the variable index
     * @param args
     * @throws Exception
     */
	static Map<String, Integer> _varId;
    
    /**
     * Directory Name of the data subsets
     */
    String _dirName;
	/**
	 * Main Method
	 * 
	 * @param args
	 * @throws Throwable
	 */
  
	public static void main(String[] args) throws Exception {
		if (args.length != 9 && args.length!=2) {
			System.err.println("Usage: java StochasticPEM dataLearnStruct dirName (EmMaxSteps EmNumRestarts EM-threshold UDtest-threshold modelName MaxIsland MaxTop)");
			System.exit(1);
		}
		// TODO Auto-generated method stub

		StochasticPEM Fast_learner = new StochasticPEM();
		Date date = new Date();            
	    System.out.println("Start Reading first part data for struct learning at "+date.toString());
		Fast_learner.initialize(args);
		
		Fast_learner.IntegratedLearn();
		
		// When there is a true model where data are sampled from, we also
		// evaluate the loglikelihood of test set on true model
		// Fast_learner.evaluateTrue();
		// Fast_learner.createdata(args);
	}

	/**
	 * Initialize All
	 * 
	 * @param args
	 * @throws IOException
	 * @throws Exception
	 */
	public void initialize(String[] args) throws IOException, Exception

	{

		if(args.length==9){
			_Origdata = new DataSet(DataSetLoader.convert(args[0]));

			_dirName = args[1];
			
			_EmMaxSteps = Integer.parseInt(args[2]);

			_EmNumRestarts = Integer.parseInt(args[3]);

			_emThreshold = Double.parseDouble(args[4]);

			_UDthreshold = Double.parseDouble(args[5]);

			_modelname = args[6];

			_maxIsland = Integer.parseInt(args[7]);
			_maxTop = Integer.parseInt(args[8]);
			
		}else if(args.length==2){
			_Origdata = new DataSet(DataSetLoader.convert(args[0]));

			_dirName = args[1];
			_EmMaxSteps = 50;
			_EmNumRestarts = 3;
			_emThreshold = 0.01;
			_UDthreshold = 3;
			_modelname = "modelname";;
			_maxIsland = 10;
			_maxTop = 15;
		}
	}

	public void IntegratedLearn() throws IOException, Exception {
		try {
			
			_modelEst = FastHLTA_learn();
			
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		//evaluate(_modelEst);
	}

	/**
	 * Build the whole HLTA layer by layer
	 * 
	 * @return BayesNet HLTA
	 * @throws Exception 
	 * @throws IOException 
	 */
	public LTM FastHLTA_learn() throws IOException, Exception {

		_workingData = _Origdata;
		LTM CurrentModel = null;
		long start = System.currentTimeMillis();
		int level = 2;
		while (true) {
			Date date = new Date();            
			System.out.println("Start learn level "+level+"at "+date.toString());
			LTM Alayer = FastLTA_flat(_workingData, level);
		//	Alayer.saveAsBif("Example level"+(level-1));
			CurrentModel = BuildHierarchy(CurrentModel, Alayer);
			
			
			long hardassign = System.currentTimeMillis();
			System.out.println("Start hard assignment at level "+level+"at "+date.toString());
			_workingData = HardAssignment(CurrentModel, Alayer);
			System.out.println("--- HardAssign Time: "
					+ (System.currentTimeMillis() -hardassign) + " ms ---");

			int a = Alayer.getInternalVars("tree").size();
			if (a <= _maxTop)
				break;
            
			level++;
		}  
		//CurrentModel.saveAsBif(_modelname+"_beforeEM");
		
		System.out.println("start Global EM at "+(new Date()).toString());
		
		File f = new File(_dirName);  
		File[] files = f.listFiles(); 
        for(int indf = 0; indf<files.length; indf++) {  
        	_Origdata  = new DataSet(DataSetLoader.convert(files[indf].getAbsolutePath()));
            System.out.println(files[indf].getAbsolutePath());
			ParallelEmLearner emLearner = new ParallelEmLearner();
			emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
			emLearner.setMaxNumberOfSteps(1);
			emLearner.setNumberOfRestarts(1);
			emLearner.setReuseFlag(true);
			emLearner.setThreshold(_emThreshold);
			long startGEM = System.currentTimeMillis();
			CurrentModel = (LTM)emLearner.em(CurrentModel, _Origdata);
			System.out.println("---The "+(indf +1)+"th--- Global EM Time: "
					+ (System.currentTimeMillis() - startGEM) + " ms ---");
		}
		
		// rename latent variables, reorder the states.
		System.out.println("--- Total Time: "
				+ (System.currentTimeMillis() - start) + " ms ---");
		System.out.println("start postprocessing at "+(new Date()).toString());

		CurrentModel = postProcessingModel(CurrentModel);
        
		
		// output final model.
		CurrentModel.saveAsBif(_modelname + ".bif");
		System.out.println("ALL finised at "+(new Date()).toString());

		//double score = ScoreCalculator.computeBic(CurrentModel, _Origdata);
		//System.out.println("BIC Score on the training data: " + score);

		return CurrentModel;
	}

	/**
	 * previous version that link all the highest level variables with a root
	 * @param currentModel
	 * @return
	 */

	/**
	 * Build One layer
	 * 
	 * @param _data
	 * @param Level
	 * @return
	 * @throws UnsupportedEncodingException 
	 * @throws FileNotFoundException 
	 */

	public LTM FastLTA_flat(DataSet _data, int Level) throws FileNotFoundException, UnsupportedEncodingException {

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
					// compute MI and find the pair with the largest MI value
					long startMI = System.currentTimeMillis();
					_mis = computeMis(_data);
					findBestPair(bestPair,_VariablesSet);
					System.out.println("======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: "
							+ (System.currentTimeMillis() - startMI)
							+ " ms ---");

				}
				ArrayList<Variable> bestP = new ArrayList<Variable>();
				findBestPair(bestP, _VariablesSet);
				ArrayList<Variable> Varstemp =
						new ArrayList<Variable>(_VariablesSet);
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
				_mis = computeMis( _data);
				findBestPair(bestPair, _VariablesSet);
				System.out.println("======================= _mis has been calculated  =================================");
				System.out.println("--- ComputingMI Time: "
						+ (System.currentTimeMillis() - startMI) + " ms ---");

			} else {
				findBestPair(bestPair, _VariablesSet);
			}

			Set<Variable> cluster = new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 3
			// variables now
			ArrayList<Variable> ClosestVariablePair =
					findShortestOutLink(_mis, null, cluster, _VariablesSet);
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
				ClosestVariablePair =
						findShortestOutLink(_mis, bestPair, cluster,
								_VariablesSet);
				cluster.add(ClosestVariablePair.get(1));
				DataSet data_proj2l =
						_data.project(new ArrayList<Variable>(cluster));
				LTM m1 =
						EmLCM_learner(m0, ClosestVariablePair.get(1), bestPair,
								data_proj2l);
				LTM minput = m1.clone();
				LTM m2 =
						EmLTM_2L_learner(minput, bestPair, ClosestVariablePair,
								data_proj2l);
				m0 = m1.clone();
				double mulModelBIC =
						ScoreCalculator.computeBic(m2, data_proj2l);
				double uniModelBIC =
						ScoreCalculator.computeBic(m1, data_proj2l);

				if (mulModelBIC - uniModelBIC > _UDthreshold) {
					if (_VariablesSet.size() - cluster.size() == 0) {
						// split m2 to 2 LCMs subModel1 and subModel2
						LTM subModel1 = m1.clone();
						for (int id = 0; id < 2; id++) {
							Edge e =
									subModel1.getNode(
											ClosestVariablePair.get(id)).getEdge(
											subModel1.getRoot());
							// Should remove node first then edge.
							subModel1.removeNode(subModel1.getNode(ClosestVariablePair.get(id)));
							subModel1.removeEdge(e);
						}
						// To get subModel2
						HashSet<String> donotUpdate = new HashSet<String>();
						// learn an LCM with ClosestVariablePair and any other
						// one node
						LTM subModel2 = new LTM();
						ArrayList<Variable> cluster_sub2_3node =
								new ArrayList<Variable>(ClosestVariablePair);
						cluster_sub2_3node.add(bestPair.get(1));
						subModel2 = LTM.createLCM(cluster_sub2_3node, 2);
						// copy parameters from m2 to submodel2
						ArrayList<Variable> var2s =
								new ArrayList<Variable>(
										subModel2.getNode(
												ClosestVariablePair.get(0)).getCpt().getVariables());
						subModel2.getNode(ClosestVariablePair.get(0)).getCpt().setCells(
								var2s,
								m2.getNode(ClosestVariablePair.get(0)).getCpt().getCells());
						var2s =
								new ArrayList<Variable>(
										subModel2.getNode(
												ClosestVariablePair.get(1)).getCpt().getVariables());
						subModel2.getNode(ClosestVariablePair.get(1)).getCpt().setCells(
								var2s,
								m2.getNode(ClosestVariablePair.get(1)).getCpt().getCells());
						donotUpdate.add(ClosestVariablePair.get(0).getName());
						donotUpdate.add(ClosestVariablePair.get(1).getName());
                    
						ParallelEmLearner emLearner = new ParallelEmLearner();
						emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
						emLearner.setMaxNumberOfSteps(_EmMaxSteps);
						emLearner.setNumberOfRestarts(_EmNumRestarts);
						emLearner.setReuseFlag(false);
						emLearner.setThreshold(_emThreshold);
						emLearner.setDontUpdateNodes(donotUpdate);
						subModel2 =
								(LTM) emLearner.em(subModel2,
										data_proj2l.project(cluster_sub2_3node));

						// remove the edge of other node
						Edge e2 =
								subModel2.getNode(bestPair.get(1)).getEdge(
										subModel2.getRoot());
						subModel2.removeNode(subModel2.getNode(bestPair.get(1)));
						subModel2.removeEdge(e2);
                     	
						updateHierarchies(subModel1, bestPair);
						updateVariablesSet(subModel1);
						updateHierarchies(subModel2, ClosestVariablePair);
						updateVariablesSet(subModel2);
						break;
					} else {
						for (int id = 0; id < 2; id++) {
							Edge e =
									m1.getNode(ClosestVariablePair.get(id)).getEdge(
											m1.getRoot());
							// Should remove node first then edge.
							m1.removeNode(m1.getNode(ClosestVariablePair.get(id)));
							m1.removeEdge(e);
						}
						updateHierarchies(m1, bestPair);
						updateVariablesSet(m1);
						break;
					}
				} else if (_VariablesSet.size() - cluster.size() == 0
						|| (cluster.size() >= _maxIsland && (_VariablesSet.size() - cluster.size()) >= 3)) {
					subModel = m1;
					updateHierarchies(subModel, bestPair);
					updateVariablesSet(subModel);
					break;
				}
			}
			i++;
		}

		// build the whole latent tree.
		System.out.println("start building flat tree at "+(new Date()).toString());

		LTM latentTree = BuildLatentTree(_data);

		return latentTree;
	}

	/**
	 * Learn a 3 node LCM
	 * 
	 */
	private LTM LCM3N(ArrayList<Variable> variables3, DataSet data_proj) {
		LTM LCM_new = LTM.createLCM(variables3, 2);
		//double[] celC  = new double[]{0.7, 0.3, 0.3, 0.7};
		//double[] celR = new double[]{0.7,0.3};
		//ArrayList<Variable> varsPair = new ArrayList<Variable>();
		//varsPair.add(LCM_new.getRoot().getVariable());
		//LCM_new.getRoot().getCpt().setCells(varsPair, celR);
		//for(AbstractNode v: LCM_new.getNodes()){
		//	BeliefNode vBel = (BeliefNode)v;
		//	if(vBel.isRoot()){
		//	varsPair.clear();
		//	varsPair = new ArrayList<Variable>(LCM_new.getNodeByName(v.getName()).getCpt().getVariables());
		//	LCM_new.getNodeByName(v.getName()).getCpt().setCells(varsPair, celC);
		//	}
		//}
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);

		LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(variables3));

		return LCM_new;
	}

	private LTM EmLCM_learner(LTM modelold, Variable x,
			ArrayList<Variable> bestPair, DataSet data_proj) {

		ArrayList<Variable> cluster3node = new ArrayList<Variable>(bestPair);
		cluster3node.add(x);
		// Learn a 3node LTM : bestpair and newly added node
		LTM LCM3var = LTM.createLCM(cluster3node, 2);
		LCM3var.randomlyParameterize();
		HashSet<String> donotUpdate = new HashSet<String>();
		
		ArrayList<Variable> var2s =
				new ArrayList<Variable>(
						LCM3var.getNode(bestPair.get(0)).getCpt().getVariables());
		LCM3var.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(0)).getCpt().getCells());
		donotUpdate.add(bestPair.get(0).getName());
		var2s =
				new ArrayList<Variable>(
						LCM3var.getNode(bestPair.get(1)).getCpt().getVariables());
		LCM3var.getNode(bestPair.get(1)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(1)).getCpt().getCells());
		donotUpdate.add(bestPair.get(1).getName());
		var2s =
				new ArrayList<Variable>(
						LCM3var.getRoot().getCpt().getVariables());
		LCM3var.getRoot().getCpt().setCells(var2s,
				modelold.getRoot().getCpt().getCells());
		donotUpdate.add(LCM3var.getRoot().getName());

		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		//fix starting point or not?
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);
		LCM3var = (LTM) emLearner.em(LCM3var, data_proj.project(cluster3node));

		LTM uniModel = modelold.clone();

		uniModel.addNode(x);

		uniModel.addEdge(uniModel.getNode(x), uniModel.getRoot());
		ArrayList<Variable> vars =
				new ArrayList<Variable>(
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

		ArrayList<Variable> cluster2BeAdded =
				new ArrayList<Variable>(unimodel.getManifestVars());
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
		ArrayList<Variable> var1 =
				new ArrayList<Variable>(lCM.getRoot().getCpt().getVariables());
		lCM.getRoot().getCpt().setCells(var1,
				unimodel.getRoot().getCpt().getCells());

		ArrayList<Variable> var2s =
				new ArrayList<Variable>(
						lCM.getNode(bestPair.get(0)).getCpt().getVariables());
		lCM.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				unimodel.getNode(bestPair.get(0)).getCpt().getCells());
		var2s =
				new ArrayList<Variable>(
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
		//fix starting point or not?
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
			var2s =
					new ArrayList<Variable>(
							multimodel.getNode(v).getCpt().getVariables());
			multimodel.getNode(v).getCpt().setCells(var2s,
					unimodel.getNode(v).getCpt().getCells());
		}

		return multimodel;
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
		}
	}

	/**
	 * Link the islands
	 * 
	 * @param _data
	 * @return
	 * @throws UnsupportedEncodingException 
	 * @throws FileNotFoundException 
	 */
	/*private BayesNet BuildLatentTree(DataSet _data) {

		AssignmentOnSingleLTM(_data);

		BayesNet latentTree = new BayesNet();

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
				BeliefNode newNode = latentTree.getNode(bNode.getVariable());
				if (!bNode.isRoot()) {
					BeliefNode parent = (BeliefNode) bNode.getParent();

					BeliefNode newParent =
							latentTree.getNode(parent.getVariable());

					latentTree.addEdge(newNode, newParent);
				}
				newNode.setCpt(bNode.getCpt().clone()); // copy the parameters
														// of manifest variables

			}
		}

		return latentTree;
	}*/
	private LTM BuildLatentTree(DataSet _data) throws FileNotFoundException, UnsupportedEncodingException {

		long LatentPostTime = System.currentTimeMillis();
		if(_latentPosts.isEmpty())
		{
			for(Variable var : _hierarchies.keySet())
			{
				LTM subModel = _hierarchies.get(var);
				updateStats(subModel,_data);
			}
		}
		System.out.println("Compute Latent Posts Time: " + (System.currentTimeMillis() - LatentPostTime) + " ms ---");

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
				}else {
					latentTree.getNodeByName(node.getName()).setCpt(
							bNode.getCpt().clone());
				}
			}
		}

		UndirectedGraph mst = learnMaximumSpanningTree(_hierarchies, _data);

		
		// Choose a root with more than 3 observed variables
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
		
		
		

		
		ArrayList<Variable> LatVarsOrdered = latentTree.getLatVarsfromTop();
		for(Variable v: LatVarsOrdered){
			if(!latentTree.getNode(v).isRoot()){
				//construct a LTM with 4 observed variables 2 latent variables
				//copy parameters
				HashSet<String> donotUpdate = new HashSet<String>();
				LTM lTM_4n = new LTM();
				BeliefNode parent  = latentTree.getNode(v).getParent();
				

				BeliefNode h2 = lTM_4n.addNode(new Variable(2));
				BeliefNode h1 = lTM_4n.addNode(new Variable(2));

				for (Variable vtemp :_bestpairs.get(parent.getName())) {
					lTM_4n.addEdge(lTM_4n.addNode(vtemp), h1);
					ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getNode(vtemp).getCpt().getVariables());
					lTM_4n.getNode(vtemp).getCpt().setCells(var2s, latentTree.getNode(vtemp).getCpt().getCells());
					donotUpdate.add(vtemp.getName());
				}
				
				for (Variable vtemp : _bestpairs.get(v.getName())){
					lTM_4n.addEdge(lTM_4n.addNode(vtemp), h2);
					ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getNode(vtemp).getCpt().getVariables());
					lTM_4n.getNode(vtemp).getCpt().setCells(var2s, latentTree.getNode(vtemp).getCpt().getCells());
					donotUpdate.add(vtemp.getName());
				}
				lTM_4n.addEdge(h2, h1);
				LTM temp = _hierarchies.get(parent.getVariable());
				ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getRoot().getCpt().getVariables());
                lTM_4n.getRoot().getCpt().setCells(var2s, temp.getRoot().getCpt().getCells());
				donotUpdate.add(h1.getName());
				
				ArrayList<Variable> cluster4var = new ArrayList<Variable>(lTM_4n.getManifestVars());
				
				ParallelEmLearner emLearner = new ParallelEmLearner();
				emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
				emLearner.setMaxNumberOfSteps(_EmMaxSteps);
				emLearner.setNumberOfRestarts(_EmNumRestarts);
				// fix starting point or not?
				emLearner.setReuseFlag(false);
				emLearner.setThreshold(_emThreshold);
				emLearner.setDontUpdateNodes(donotUpdate);
				
				LTM LTM4var = (LTM) emLearner.em(lTM_4n, _data.project(cluster4var));
				
				ArrayList<Variable> vars = new ArrayList<Variable>(latentTree.getNode(v).getCpt().getVariables());
				latentTree.getNode(v).getCpt().setCells(vars, LTM4var.getNode(h2.getVariable()).getCpt().getCells());
			}
		}

	/*	ArrayList<Variable> LatVarsOrdered = latentTree.getLatVarsfromTop();
		for(Variable v: LatVarsOrdered){
			if(!latentTree.getNode(v).isRoot()){
				// ref_node: R, par(R)= par(v).Obtain the P(R|par(R))
			   String ref_node = _refNodes.get(latentTree.getNode(v).getParent().getName());
			   Matrix P_refgivPar = FunToMat(latentTree.getNodeByName(ref_node));
			   
			   String sef_node  = _refNodes.get(latentTree.getNode(v).getName());
			   Matrix P_sefgivL = FunToMat(latentTree.getNodeByName(sef_node));
			   
			   //Calculate the joint distribution
			   ArrayList<Variable> Temp_AF = new ArrayList<Variable>();
			   Temp_AF.add(latentTree.getNodeByName(sef_node).getVariable());
			   Temp_AF.add(latentTree.getNodeByName(ref_node).getVariable());
			   DataSet data_AF = _data.project(Temp_AF,false);
			   Matrix P_AF = computeJointDist(Temp_AF, data_AF);
			   
			   //P_{A|par(L)} = Normalize{P_AF *P_{F|par(L)}^{-1}.transpose}
	  	  	   MatrixInverter inverter_refgivPar = P_refgivPar.withInverter(LinearAlgebra.GAUSS_JORDAN);
	  		   Matrix P_sefgivPar = Normalize_Col(P_AF.multiply( inverter_refgivPar.inverse(LinearAlgebra.DENSE_FACTORY).transpose()));
               
	  		   //P_{A|L}.inverse
	  		    MatrixInverter inverter_sefgivL = P_sefgivL.withInverter(LinearAlgebra.GAUSS_JORDAN);
			    Matrix P_sefgivL_inv = inverter_sefgivL.inverse(LinearAlgebra.DENSE_FACTORY);
	  		

	  	  	    //P_{L|par(L)}= P_{A|L}.inv * P_{A|par(L)}
	  	  	    Matrix P_cond = P_sefgivL_inv.multiply(P_sefgivPar);
	            //P_cond=Normalize_Col(checkMatNeg(P_cond));

				setVdis(latentTree.getNode(v).getVariable(), P_cond, latentTree);   
			}
		}*/
		
     //   latentTree.saveAsBif("alayer.bif");
		return latentTree;
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
		private class Implementation {
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
			private class ParallelComputation extends RecursiveAction {

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

	protected ArrayList<double[]> computeMis(DataSet _data) {
		return computeMisByCount(_data);
	}

	/**
	 * Compute pairwise MI between the specified list of variable based on the
	 * posterior distributions P(Y|d) and the training data. Also swap the pair
	 * of variables with the largest MI to the head of the list.
	 */
/*	protected Map<Integer, Map<Integer, Double>> computeMisPar(
			ArrayList<Variable> bestPair, DataSet _data) {
		// normalize the MI or not
		boolean normalize = false;

		List<Variable> vars = new ArrayList<Variable>(_VariablesSet);

		EmpiricalMiComputer computer =
				new EmpiricalMiComputer(_data, vars, normalize);
		Map<Integer, Map<Integer, Double>> miArray = computer.computerPairwise();

		return miArray;
	}*/

	protected ArrayList<double[]> computeMisByCount(DataSet _data) {
	

		EmpiricalMiComputerForBinaryData computer =
				new EmpiricalMiComputerForBinaryData(_data, _Variables);
		ArrayList<double[]> miArray = computer.computerPairwise();

		return  miArray;
	}

	/*private static Map<Integer, Map<Integer, Double>> processMi(
			List<Variable> bestPair, double[][] miArray, List<Variable> vars) {
		// convert the array to map

		// initialize the data structure for pairwise MI
		Map<Integer, Map<Integer, Double>> mis =
				new HashMap<Integer, Map<Integer, Double>>(vars.size());

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		for (int i = 0; i < vars.size(); i++) {
			double[] row = miArray[i];

			Map<Integer, Double> map =
					new HashMap<Integer, Double>(vars.size());
			for (int j = 0; j < vars.size(); j++) {
				map.put(_varId.get(vars.get(j).getName()), row[j]);

				// find the best pair
				if (row[j] > maxMi) {
					maxMi = row[j];
					first = vars.get(i);
					second = vars.get(j);
				}
			}

			mis.put(_varId.get(vars.get(i).getName()), map);

			// to allow garbage collection
			miArray[i] = null;
		}

		// set the best pair
		bestPair.add(first);
		bestPair.add(second);

		return mis;

	}*/

	/**
	 * 
	 * Return the best pair of variables with max MI in _mis.
	 */
	private void findBestPair(ArrayList<Variable> bestPair,
			Set<Variable> VariablesSet) {
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
			int iId = _varId.get(vi.getName());
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);
				int jId = _varId.get(vj.getName());
				double mi = _mis.get(iId)[jId];

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
	 * Return true if and only if the whole clustering procedure is done, or
	 * equivalently, there is only one hierarchy left.
	 */
	private boolean isDone() {
		return _VariablesSet.size() < 1;
	}

	/**
	 * Find the closest variable to cluster. Note: Never move the bestpair out
	 * 
	 * @param mis
	 * @param cluster
	 * @return
	 */
	private ArrayList<Variable> findShortestOutLink(
			ArrayList<double[]> mis,
			ArrayList<Variable> bestPair, Set<Variable> cluster,
			Set<Variable> VariablesSet) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			boolean a = bestPair == null;
			if (a || !bestPair.contains(inCluster)) {
				for(int l = 0; l< mis.get(_varId.get(inCluster.getName())).length;l++ ){
				//for (Entry<Integer, Double> entry : mis.get(_varId.get(inCluster.getName())).entrySet()) {
					Variable outCluster =_Variables.get(l);
					double mi = mis.get(_varId.get(inCluster.getName()))[l];

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


	/*
	 * public void createdata(String[] args) throws IOException, Exception {
	 * 
	 * //True model _model = new LTM(); Parser parser = new BifParser(new
	 * FileInputStream(args[0]),"UTF-8"); parser.parse(_model);
	 * _Variables.addAll(_model.getManifestVars());
	 * 
	 * doSample(Integer.parseInt(args[1]),Integer.parseInt(args[1]));
	 * 
	 * }
	 */

	/**
	 * Stack the results
	 * @param _data
	 */
private LTM BuildHierarchy(LTM OldModel, LTM tree) {
		
		LTM CurrentModel = new LTM();
		if (OldModel == null) {
			return tree;
		}
		
		CurrentModel = OldModel;

		
		Set<Edge> edgeSet = new HashSet<Edge>();

		for (Edge e : OldModel.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			if (tree.getNode(head) != null && tree.getNode(tail) != null) {
				edgeSet.add(e);
			}
		}


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

	

		
		for(AbstractNode nd: tree.getNodes()){
			BeliefNode bnd  = (BeliefNode)nd;
			if(!bnd.isRoot()){
				
			ArrayList<Variable> pair = new ArrayList<Variable>();
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getVariable());
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getParent().getVariable());

			double[] Cpt = bnd.getCpt().getCells();
		
			CurrentModel.getNodeByName(nd.getName()).getCpt().setCells(pair,Cpt);
			}else {
				CurrentModel.getNodeByName(nd.getName()).setCpt(
						bnd.getCpt().clone());
			}
		}

		return CurrentModel;
	}	
	
	/*	private BayesNet BuildHierarchy(BayesNet OldModel, BayesNet tree) {

		BayesNet CurrentModel = new BayesNet();
		if (OldModel == null) {
			return tree;
		}

		CurrentModel = OldModel;

		for (Variable v : tree.getInternalVars("BayesNet")) {
			CurrentModel.addNode(v);
		}
		for (Edge e : tree.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			CurrentModel.addEdge(CurrentModel.getNodeByName(head),
					CurrentModel.getNodeByName(tail));
		}

		for (AbstractNode nd : tree.getNodes()) {
			BeliefNode bnd = (BeliefNode) nd;
			if (!bnd.isRoot()) {

				ArrayList<Variable> pair = new ArrayList<Variable>();
				pair.add(CurrentModel.getNodeByName(bnd.getName()).getVariable());
				pair.add(CurrentModel.getNodeByName(bnd.getName()).getParent().getVariable());

			
				double[] Cpt = bnd.getCpt().getCells();

				CurrentModel.getNodeByName(nd.getName()).getCpt().setCells(
						pair, Cpt);
			} else {
				CurrentModel.getNodeByName(nd.getName()).setCpt(
						bnd.getCpt().clone());
			}

		}

		return CurrentModel;
	}*/

	

	/**
	 * Initialize before building each layer
	 * 
	 * @param data
	 */

	/**
	 * Initialize before building each layer
	 * 
	 * @param data
	 */

	protected void initialize(DataSet data) {
		//System.out.println("=== Initialization ===");

		// initialize data structures for P(Y|d).
		_latentPosts = new HashMap<Variable, Map<DataCase, Function>>();

		// initialize hierarchies
		// _hirearchies will be used to keep all LCMs found by U-test.
		_hierarchies = new HashMap<Variable, LTM>();

		_Variables = new ArrayList<Variable>();
		_VariablesSet = new HashSet<Variable>();
		_mis = new ArrayList<double[]>();

		// add all manifest variable to variable set _VariableSet.
		for (Variable var : data.getVariables()) {
			_VariablesSet.add(var);
			_Variables.add(var);
		}
		
		_varId = new HashMap<String, Integer>();
		for(int i =0;i<_Variables.size();i++){
			_varId.put(_Variables.get(i).getName(), i);
		}
	}


	
	private LTM postProcessingModel(LTM model) {

	

		HashMap<Integer, HashSet<String>> varDiffLevels =
				processVariables(model);
		HashMap<Integer, Integer> levelAndIndex =
				new HashMap<Integer, Integer>();

		// reorderStates first.
		model = reorderStates(model, varDiffLevels);

		int topLevelIndex = varDiffLevels.size() - 1;

		for (int i = 1; i < topLevelIndex + 1; i++) {
			levelAndIndex.put(i, 0);
		}

		HashSet<String> topLevel = varDiffLevels.get(topLevelIndex);

		// renameVariables
		for (String str : topLevel) {
			processName(model, str, topLevelIndex, levelAndIndex, varDiffLevels);
		}

		model = smoothingParameters(model);

		return model;
	}

	private LTM reorderStates(LTM bn,
			HashMap<Integer, HashSet<String>> varDiffLevels) {
		// inference engine
		CliqueTreePropagation ctp = new CliqueTreePropagation(bn);
		ctp.clearEvidence();
		ctp.propagate();
		Variable[] latents = new Variable[1];
		int[] states = new int[1];

		// reorder states for each latent variable
		for (Variable latent : bn.getInternalVars("tree")) {
			latents[0] = latent;

			// calculate severity of each state
			int card = latent.getCardinality();
			double[] severity = new double[card];
			int VarLevel = 0;
			for (int key : varDiffLevels.keySet()) {
				HashSet<String> set = varDiffLevels.get(key);
				if (set.contains(latent.getName())) {
					VarLevel = key;
					break;
				}
			}

			Set<DirectedNode> setNode = new HashSet<DirectedNode>();
			Set<DirectedNode> childSet = bn.getNode(latent).getChildren();
			for (DirectedNode node : childSet) {
				Variable var = ((BeliefNode) node).getVariable();

				if (!varDiffLevels.get(VarLevel - 1).contains(var.getName()))
					continue;

				if (((BeliefNode) node).isLeaf()) {
					setNode.add((DirectedNode) node);
				} else {
					for (AbstractNode nodeDes : bn.getNode(var).getDescendants()) {
						if (((BeliefNode) nodeDes).isLeaf()) {
							setNode.add((DirectedNode) nodeDes);
						}
					}
				}
			}
			List<Map.Entry<Variable, Double>> list =
					SortChildren(latent, setNode, ctp);
			ArrayList<Variable> collectionVar = new ArrayList<Variable>();
			if (list.size() > 5) {
				for (int Id = 0; Id < 5; Id++) {
					collectionVar.add(list.get(Id).getKey());
				}
			} else {
				for (DirectedNode node : setNode) {
					Variable manifest = ((BeliefNode) node).getVariable();
					collectionVar.add(manifest);
				}
			}
			for (int i = 0; i < card; i++) {
				states[0] = i;

				ctp.setEvidence(latents, states);
				ctp.propagate();

				// accumulate expectation of each manifest variable

				for (Variable manifest : collectionVar) {
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

			// for More than 2 states,but now we don't need bubble sort
			// bubble sort
			/*
			 * for (int i = 0; i < card - 1; i++) { for (int j = i + 1; j <
			 * card; j++) { if (severity[i] > severity[j]) { int tmpInt =
			 * order[i]; order[i] = order[j]; order[j] = tmpInt;
			 * 
			 * double tmpReal = severity[i]; severity[i] = severity[j];
			 * severity[j] = tmpReal; } } }
			 */
			if (severity[0] - severity[1] > 0.05) {

				order[0] = 1;
				order[1] = 0;

				double tmpReal = severity[0];
				severity[0] = severity[1];
				severity[1] = tmpReal;
			}
			/*
			 * else{ Map<Variable,Double> Freq = _Origdata.getFreq(); Function
			 * fun = bn.getNode(latent).getCpt().marginalize(latent);
			 * fun.divide(2); double[] dist = fun.getCells(); double c = 0;
			 * for(Variable mani: collectionVar){ c = c + Freq.get(mani); } c =
			 * c/collectionVar.size()/_Origdata.getTotalWeight(); if(Math.abs(c
			 * - dist[0])<Math.abs(c-dist[1])){ order[0] = 1; order[1] = 0;
			 * 
			 * double tmpReal = severity[0]; severity[0] = severity[1];
			 * severity[1] = tmpReal; } }
			 */
			// reorder states
			bn.getNode(latent).reorderStates(order);
			latent.standardizeStates();
		}

		return bn;
	}

	private void processName(BayesNet model, String str, int level,
			HashMap<Integer, Integer> levelAndIndex,
			HashMap<Integer, HashSet<String>> varDiffLevels) {
		if (varDiffLevels.get(0).contains(str))
			return;

		Set<DirectedNode> set = model.getNodeByName(str).getChildren();

		changeName(model, str, level, levelAndIndex);

		for (DirectedNode child : set) {
			if (!varDiffLevels.get(level - 1).contains(child.getName()))
				continue;

			processName(model, child.getName(), level - 1, levelAndIndex,
					varDiffLevels);
		}
	}

	private void changeName(BayesNet model, String str, int level,
			HashMap<Integer, Integer> levelAndIndex) {
		BeliefNode var = model.getNodeByName(str);

		int index = levelAndIndex.get(level) + 1;
		String newName = "Z" + level + index;
		var.setName(newName);

		levelAndIndex.put(level, index);
	}

	private HashMap<Integer, HashSet<String>> processVariables(BayesNet model) {
		HashMap<Integer, HashSet<String>> varDiffLevels =
				new HashMap<Integer, HashSet<String>>();

		Set<Variable> internalVar = model.getInternalVars("tree");
		Set<Variable> leafVar = model.getLeafVars("tree");

		HashSet<String> levelOne = new HashSet<String>();
		for (Variable v : leafVar) {
			levelOne.add(v.getName());
		}
		varDiffLevels.put(0, levelOne);

		int level = 0;
		while (internalVar.size() > 0) {
			HashSet<String> levelVar = varDiffLevels.get(level);
			level++;

			HashSet<String> newSet = new HashSet<String>();
			for (String s : levelVar) {
				String parent = model.getNodeByName(s).getParent().getName();

				if (parent != null) {
					internalVar.remove(model.getNodeByName(parent).getVariable());
					newSet.add(parent);
				}
			}
			varDiffLevels.put(level, newSet);
		}

		return varDiffLevels;
	}

	private List<Map.Entry<Variable, Double>> SortChildren(Variable var,
			Set<DirectedNode> nodeSet, CliqueTreePropagation ctp) {
		Map<Variable, Double> children_mi = new HashMap<Variable, Double>();

		for (DirectedNode node : nodeSet) {
			Variable child = ((BeliefNode) node).getVariable();
			double mi = computeMI(var, child, ctp);
			children_mi.put(child, mi);
		}

		List<Map.Entry<Variable, Double>> List =
				Utils.sortByDescendingOrder(children_mi);

		return List;
	}

	private double computeMI(Variable x, Variable y, CliqueTreePropagation ctp) {
		List<Variable> xyNodes = new ArrayList<Variable>();
		xyNodes.add(x);
		xyNodes.add(y);

		return Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
	}

	
	private LTM smoothingParameters(LTM model)
	{
		for(AbstractNode node : model.getNodes())
		{
			Function fun = ((BeliefNode)node).getCpt();
					
			for(int i=0; i<fun.getDomainSize(); i++)
			{
				if(fun.getCells()[i]<0.01){
					fun.getCells()[i]=0.01;
				}else if(fun.getCells()[i]>0.99){
					fun.getCells()[i]=0.99;
				}
			}
			
			model.getNodeByName(node.getName()).setCpt(fun);
		}
		return model;
	}
	
	/**
	 * Update the collections of P(Y|d). Specifically, remove the entries for
	 * all the latent variables in the given sub-model except the root, and
	 * compute P(Y|d) for the latent variable at the root and each data case d.
	 */
	private void updateStats(LTM subModel, DataSet _data) {
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
	
	private UndirectedGraph learnMaximumSpanningTree(
			Map<Variable, LTM> hierarchies, DataSet _data) {
		// initialize the data structure for pairwise MI
		List<StringPair> pairs = new ArrayList<StringPair>();

		// the collection of latent variables.
		List<Variable> vars = new ArrayList<Variable>(hierarchies.keySet());

		List<Variable> varPair = new ArrayList<Variable>(2);
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
				Function pairDist = computeEmpDist(varPair,_data);
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
        //move the node with more than 2 variables to the first place
		boolean flag = false;
		for (Variable var : hierarchies.keySet()) {
			String name = var.getName();
			mst.addNode(name);
        
			if(hierarchies.get(var).getLeafVars().size()>=3&& !flag){
				mst.move2First(name);
				flag = true;
			}
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
	
	private DataSet HardAssignment(LTM CurrentModel, LTM latentTree) {
		ArrayList<DataCase> data = _Origdata.getData();

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
			ctp.setEvidence(_Origdata.getVariables(), states);
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

		return da;
	}
}

