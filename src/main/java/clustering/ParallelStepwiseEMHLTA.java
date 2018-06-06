package clustering;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.UndirectedGraph;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.learner.ParallelStepwiseEmLearner;
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
import org.latlab.learner.SparseDataSet;

/**
 * Hierarchical Latent Tree Analysis for topic detection.
 * 
 * @author Farhan Khawar : code adapted from Peixian Chen
 * train-data:      The data file for learning HLTMs, it can be ARFF format or CSV format. 
 * EmMaxSteps:        The maximum number of steps for PEMHLTA. 
 * EmNumRestarts:     The number of random starts for PEMHLTA.
 * EM-threshold:      The converge threshold for PEMHLTA.
 * UDtest-threshold: The threshold for UD-test.
 * output-model:	The name of obtained model.
 * MaxIsland:     The maximum number of variables in one island
 * MaxTop:		The maximum number of latent variables at the top level
 * 
 */

public class ParallelStepwiseEMHLTA {

	private LTM _model;
	private BayesNet _modelEst;
	/**
	 * Original data.
	 */
	private SparseDataSet _OrigSparseData;
	
	private DataSet _OrigDenseData;

	private DataSet _workingData;

	private DataSet _test;
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
	 * The ArrayList of manifest variables with orders.
	 */
	private ArrayList<Variable> _Variables;

	/**
	 * The collection of manifest variables that wait to do UD-test.
	 */
	private Set<Variable> _VariablesSet;

	/**
	 * The collection of posterior distributions P(Y|d) for each latent variable
	 * Y at the root of a hierarchy and each data case d in the training data.
	 */
	private Map<Variable, Map<DataCase, Function>> _latentPosts;

	private int _globalEMmaxSteps;
	private int _maxEpochs;
	private int _sizeBatch;
	private String _sizeFirstBatch;
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
	 * The reference node for children of every latent variable
	 */
	Map<String, String> _refNodes = new HashMap<String, String>();

	/**
	 * Name the model you obtain
	 */
	String _modelname;
   
	/**
     * Store the variable index
     */
	static Map<String, Integer> _varId;
	
	
	/**
	 * Main Method
	 * 
	 * @param args
	 * @throws Throwable
	 */

	public static void main(String[] args) throws Exception {
		if (args.length != 12 &&args.length != 1 &&args.length != 3 &&args.length!= 0) {
			System.err.println("Usage: java PEMHLTA trainingdata (EmMaxSteps EmNumRestarts EM-threshold UDtest-threshold outputmodel MaxIsland MaxTop GlobalsizeBatch GlobalMaxEpochs GlobalEMmaxsteps FirstBatch");
			System.exit(1);
		}
		// TODO Auto-generated method stub

		if(args.length ==12||args.length ==1||args.length ==0){
			ParallelStepwiseEMHLTA Fast_learner = new ParallelStepwiseEMHLTA();
			Fast_learner.initialize(args);
			
			
			Fast_learner.IntegratedLearn();
		}
		if(args.length==3){
			ParallelStepwiseEMHLTA test = new ParallelStepwiseEMHLTA();
			test.testtest(args);	
		}
		
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

		// _model = new LTM();
		// Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		// parser.parse(_model);
        System.out.println("Initializing......");
		// Read the data set

        if(args.length==0){
        	 _OrigSparseData = new SparseDataSet("./data/SampleData_5000.arff");
        	 
        }else{
		_OrigSparseData = new SparseDataSet(args[0]);
        }

		if(args.length==12){
		_EmMaxSteps = Integer.parseInt(args[1]);

		_EmNumRestarts = Integer.parseInt(args[2]);

		_emThreshold = Double.parseDouble(args[3]);

		_UDthreshold = Double.parseDouble(args[4]);

		_modelname = args[5];

		_maxIsland = Integer.parseInt(args[6]);
		_maxTop = Integer.parseInt(args[7]);
		_sizeBatch = Integer.parseInt(args[8]);
		_maxEpochs = Integer.parseInt(args[9]);
		_globalEMmaxSteps = Integer.parseInt(args[10]);
		_sizeFirstBatch = args[11];
		if(_sizeFirstBatch.contains("all")){
            _OrigDenseData = _OrigSparseData.getWholeDenseData();
		}else{
            _OrigDenseData = _OrigSparseData.GiveDenseBatch(Integer.parseInt(_sizeFirstBatch));
		}
		}else{
			_EmMaxSteps =50;
			_EmNumRestarts=3;
			_emThreshold=0.01;
			_UDthreshold=3;
			_modelname ="HLTAModel";
			_maxIsland = 15;
			_maxTop =30;
			_sizeBatch =500;
			_maxEpochs = 10;
			_globalEMmaxSteps =100;
			_sizeFirstBatch = "all";
            _OrigDenseData = _OrigSparseData.getWholeDenseData();
		}
	}

	
	public void testtest(String[] args) throws IOException, Exception{
		 _model = new LTM();
		 Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		 parser.parse(_model);
		 
		 _test = new DataSet(DataSetLoader.convert(args[1]));
		 
		 double perLL = evaluate(_model);
		 BufferedWriter BWriter = new  BufferedWriter(new FileWriter(args[2]+File.separator+"EvaluationResult.txt"));
         BWriter.write("Per-document log-likelihood =  "+perLL);
         BWriter.close();
	}
	
	
	
	
	public void IntegratedLearn() {
		try {
			
			_modelEst = FastHLTA_learn();
			
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	/**
	 * Build the whole HLTA layer by layer
	 * 
	 * @return BayesNet HLTA
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public LTM FastHLTA_learn() throws FileNotFoundException,
			UnsupportedEncodingException {
			_workingData = _OrigDenseData; // dense data is related to the number of datacases, either whole data for specified by the user
			LTM CurrentModel = null;
			long start = System.currentTimeMillis();
			System.out.println("Start model construction...");
			int level = 2;
			while (true) {
			
			LTM Alayer = FastLTA_flat_handle(_workingData, level);
			

			CurrentModel = BuildHierarchy(CurrentModel, Alayer);
			
		
			int a = Alayer.getInternalVars("tree").size();
			if (a <= _maxTop)
				break;
			
			// all but first level will have global hardassignbment, first level will have local parallel hardassginment i.e. for each submodel
			//if(level!=2) {
			//if (_Variables.size()<=500) {
			System.out.println("Start hard assignment...");
			long startHardAssign = System.currentTimeMillis();
			_workingData = HardAssignment(CurrentModel, Alayer,_OrigDenseData); // don't do this for the first layer
			/*ParallelHardAssignment ph = new ParallelHardAssignment();
			ph.ParallelHardAssignmentCompute(CurrentModel,Alayer,_OrigDenseData);
			_workingData = ph.getData();*/
			//ph.ParallelHardAssignmentCompute(CurrentModel,Alayer,_workingData);
			
			System.out.println("--- Hard Assignment Time: "
					+ (System.currentTimeMillis() - startHardAssign) + " ms ---");
			
			//}
			
			level++;
		}
		
		System.out.println("Model construction is completed. EM parameter estimation begins...");
		ParallelStepwiseEmLearner emLearner = new ParallelStepwiseEmLearner();
		emLearner.setMaxNumberOfSteps(_globalEMmaxSteps);
		emLearner.setNumberOfRestarts(1);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(_emThreshold);
	    emLearner.setBatchSize(_sizeBatch);
	    emLearner.setMaxNumberOfEpochs(_maxEpochs);
		
		long startGEM = System.currentTimeMillis();
		CurrentModel = (LTM)emLearner.em(CurrentModel, _OrigSparseData);
	
		
		System.out.println("--- Global EM Time: "
				+ (System.currentTimeMillis() - startGEM) + " ms ---");
		
		// rename latent variables, reorder the states.
		System.out.println("--- Total Time: "
				+ (System.currentTimeMillis() - start) + " ms ---");
		CurrentModel = postProcessingModel(CurrentModel);

		// output final model.
		CurrentModel.saveAsBif(_modelname + ".bif");


		return CurrentModel;
	}



	/**
	 * Build One layer
	 * 
	 * @param _data
	 * @param Level
	 * @return
	 * @throws UnsupportedEncodingException 
	 * @throws FileNotFoundException 
	 */
	public LTM FastLTA_flat_handle(DataSet _data, int Level) throws FileNotFoundException, UnsupportedEncodingException {

		initialize(_data);
		System.out.println("===========Building Level "+ (Level-1)+ "=============");
		// Call lcmLearner iteratively and learn the LCMs.
		
		//if (_Variables.size()>500) {
		//if(Level==2) {
		LTM latentTree;
		//DataSet hardAssign=new DataSet();
		
		ParallelLayer pl= new ParallelLayer();
		
		//pl.parallelLayer(_OrigDenseData, _data , _UDthreshold,
		// _EmMaxSteps, _EmNumRestarts,  _emThreshold, _maxIsland, Level, _Variables); // latentTree and hardAssign should get value from the parallelLayer
		
		latentTree = pl.getTmpTree();
		_workingData =pl.getTmpData();
		//System.out.println("Number of variables in data:"+_workingData.getDimension()+" | "+"Number of variables in tree: "+((BayesNet)(latentTree)).getNumberOfNodes());
		//latentTree.saveAsBif("firstlayer.bif");
		return latentTree;

	}
	
	public LTM FastLTA_flat(DataSet _data, int Level) throws FileNotFoundException, UnsupportedEncodingException {

		int i = 1;
		initialize(_data);
		System.out.println("===========Building Level "+ (Level-1)+ "=============");
		// Call lcmLearner iteratively and learn the LCMs.
		
		//if (true) {
		if (_Variables.size()>500) {
		//if(Level==2) {
			LTM latentTree;
			//DataSet hardAssign=new DataSet();
			
			ParallelLayer pl= new ParallelLayer();
			
			//pl.parallelLayer(_OrigDenseData, _data , _UDthreshold,
			// _EmMaxSteps, _EmNumRestarts,  _emThreshold, _maxIsland, Level, _Variables); // latentTree and hardAssign should get value from the parallelLayer
			
			latentTree = pl.getTmpTree();
			_workingData =pl.getTmpData();
			//System.out.println("Number of variables in data:"+_workingData.getDimension()+" | "+"Number of variables in tree: "+((BayesNet)(latentTree)).getNumberOfNodes());
			//latentTree.saveAsBif("firstlayer.bif");
			return latentTree;
		}
		
		while (!isDone()) {
			System.out.println("======================= Learn Island : " + i
					+ " , number of variables left: " + _VariablesSet.size()
					+ "  =================================");
			if (_VariablesSet.size() == 3) {
				if (_mis.isEmpty()) {
					// compute MI and find the pair with the largest MI value
					long startMI = System.currentTimeMillis();
					_mis = computeMis(_data,_Variables);
					System.out.println("======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: "
							+ (System.currentTimeMillis() - startMI)
							+ " ms ---");

				}
				ArrayList<Variable> bestP = new ArrayList<Variable>();
				findBestPair(bestP, _VariablesSet,_varId,_mis);
			//	System.out.println("Best Pair " + bestP.get(0).getName() +" and " + bestP.get(1).getName());
				ArrayList<Variable> Varstemp =
						new ArrayList<Variable>(_VariablesSet);
				DataSet data_proj = _data.project(Varstemp);
				LTM subModel = LCM3N(Varstemp, data_proj,_EmMaxSteps, _EmNumRestarts, _emThreshold);
				updateHierarchies(subModel, bestP,_bestpairs, _hierarchies);
				updateVariablesSet(subModel,_VariablesSet);
				break;
			}

			ArrayList<Variable> bestPair = new ArrayList<Variable>();
			// _mis only needs to compute once

			if (_mis.isEmpty()) {
				// compute MI and find the pair with the largest MI value
				long startMI = System.currentTimeMillis();
				_mis = computeMis( _data,_Variables);
				findBestPair(bestPair, _VariablesSet, _varId, _mis);
				System.out.println("======================= _mis has been calculated  =================================");
				System.out.println("--- ComputingMI Time: "
						+ (System.currentTimeMillis() - startMI) + " ms ---");

			} else {
				findBestPair(bestPair, _VariablesSet,_varId, _mis);
			}

			Set<Variable> cluster = new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 3
			// variables now
			ArrayList<Variable> ClosestVariablePair =
					findShortestOutLink(_mis, null, cluster, _VariablesSet, _varId, _Variables);
			ArrayList<Variable> cluster_3n = new ArrayList<Variable>(bestPair);

			// cluster_3n is an array containing 3 variables : bestpair and
			// ClosestVariablePair.get(1)
			LTM subModel = null;
			if (!ClosestVariablePair.isEmpty()) {
				cluster_3n.add(ClosestVariablePair.get(1));
				cluster.add(ClosestVariablePair.get(1));
			}
			// m0
			LTM m0 = LCM3N(cluster_3n, _data.project(cluster_3n),_EmMaxSteps, _EmNumRestarts, _emThreshold);
			// cluster is the working set
			while (true) {
				ClosestVariablePair =
						findShortestOutLink(_mis, bestPair, cluster,
								_VariablesSet, _varId, _Variables);
				cluster.add(ClosestVariablePair.get(1));
				DataSet data_proj2l =
						_data.project(new ArrayList<Variable>(cluster));
				LTM m1 =
						EmLCM_learner(m0, ClosestVariablePair.get(1), bestPair,
								data_proj2l,_EmMaxSteps, _EmNumRestarts, _emThreshold);
				LTM minput = m1.clone();
				LTM m2 =
						EmLTM_2L_learner(minput, bestPair, ClosestVariablePair,
								data_proj2l,_EmMaxSteps, _EmNumRestarts, _emThreshold);
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
						//subModel2 = LTM.createLCM(cluster_sub2_3node, 2);
						subModel2 = LCM3N(cluster_sub2_3node, _data.project(cluster_sub2_3node),_EmMaxSteps, _EmNumRestarts, _emThreshold);
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
                     	
						updateHierarchies(subModel1, bestPair,_bestpairs, _hierarchies);
						updateVariablesSet(subModel1,_VariablesSet);
						updateHierarchies(subModel2, ClosestVariablePair,_bestpairs, _hierarchies);
						updateVariablesSet(subModel2,_VariablesSet);
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
						updateHierarchies(m1, bestPair,_bestpairs, _hierarchies);
						updateVariablesSet(m1,_VariablesSet);
						break;
					}
				} else if (_VariablesSet.size() - cluster.size() == 0
						|| (cluster.size() >= _maxIsland && (_VariablesSet.size() - cluster.size()) >= 3)) {
					subModel = m1;
					updateHierarchies(subModel, bestPair,_bestpairs, _hierarchies);
					updateVariablesSet(subModel,_VariablesSet);
					break;
				}
			}
			i++;
		}

		// link the islands.
		LTM latentTree = BuildLatentTree(_data);

		return latentTree;
	}

	/**
	 * Learn a 3 node LCM
	 * 
	 */
	public static LTM LCM3N(ArrayList<Variable> variables3, DataSet data_proj, int _EmMaxSteps, int _EmNumRestarts, double _emThreshold ) {
		LTM LCM_new = LTM.createLCM(variables3, 2);
		
	
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);

		LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(variables3));

		return LCM_new;
	}

	public static LTM EmLCM_learner(LTM modelold, Variable x,
			ArrayList<Variable> bestPair, DataSet data_proj,int _EmMaxSteps,int _EmNumRestarts,double _emThreshold) {

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

	public static LTM EmLTM_2L_learner(LTM unimodel, ArrayList<Variable> bestPair,
			ArrayList<Variable> ClosestPair, DataSet data_proj,int _EmMaxSteps,int _EmNumRestarts,double _emThreshold) {

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
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);

		LTM LTM4var = (LTM) emLearner.em(lCM, data_proj.project(cluster4var));
		
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
	public static void updateHierarchies(LTM subModel, ArrayList<Variable> bestPair,Map<String, ArrayList<Variable>> _bestpairs,Map<Variable, LTM> _hierarchies) {
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
	public static void updateVariablesSet(LTM subModel,Set<Variable> _VariablesSet  ) {
		BeliefNode root = subModel.getRoot();

		for (DirectedNode child : root.getChildren()) {
			_VariablesSet.remove(((BeliefNode) child).getVariable());
		}
	}

	private LTM BuildLatentTree(DataSet _data) throws FileNotFoundException, UnsupportedEncodingException {

	//	long LatentPostTime = System.currentTimeMillis();
		if(_latentPosts.isEmpty())
		{
			for(Variable var : _hierarchies.keySet())
			{
				LTM subModel = _hierarchies.get(var);
				updateStats(subModel,_data,_latentPosts);
			}
		}
	//	System.out.println("Compute Latent Posts Time: " + (System.currentTimeMillis() - LatentPostTime) + " ms ---");

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
		
		// learn MST
		UndirectedGraph mst = learnMaximumSpanningTree(_hierarchies, _data,_latentPosts);

		
		// Choose a root with more than 3 observed variables
		Queue<AbstractNode> frontier = new LinkedList<AbstractNode>();
		frontier.offer(mst.getNodes().peek()); // get the head of mst

		// add the edges among latent nodes.
		while (!frontier.isEmpty()) {
			AbstractNode node = frontier.poll(); 
			DirectedNode dNode =
					(DirectedNode) latentTree.getNode(node.getName());

			for (AbstractNode neighbor : node.getNeighbors()) {// neighbors of the current node from the MST
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
				emLearner.setReuseFlag(false);
				emLearner.setThreshold(_emThreshold);
				emLearner.setDontUpdateNodes(donotUpdate);
				
				LTM LTM4var = (LTM) emLearner.em(lTM_4n, _data.project(cluster4var));
				
				ArrayList<Variable> vars = new ArrayList<Variable>(latentTree.getNode(v).getCpt().getVariables());
				latentTree.getNode(v).getCpt().setCells(vars, LTM4var.getNode(h2.getVariable()).getCpt().getCells());
			}
		}

	
		/*
		 * EM for each layer if needed
		 */
		
	/*	ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(20);
		emLearner.setNumberOfRestarts(1);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(_emThreshold);

		latentTree = (LTM) emLearner.em(latentTree, _data.synchronize(latentTree));     
	*/
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
			Function pairDist = computeEmpDist(Arrays.asList(vi, vj), data, _latentPosts);
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

	public static ArrayList<double[]> computeMis(DataSet _data,ArrayList<Variable> Variables  ) {
		return computeMisByCount(_data,Variables);
	}


	public static ArrayList<double[]> computeMis(DataSet _data,ArrayList<Variable> Variables ,String cosine ) {
		return computeMisByCount(_data,Variables,cosine);
	}
	
	protected static ArrayList<double[]> computeMisByCount(DataSet _data,ArrayList<Variable> Variables, String cosine ) {


		EmpiricalMiComputerForBinaryDataStep computer =
				new EmpiricalMiComputerForBinaryDataStep(_data, Variables);
		ArrayList<double[]> miArray = computer.computerPairwise(cosine);

		return  miArray;
	}

	
	protected static ArrayList<double[]> computeMisByCount(DataSet _data,ArrayList<Variable> Variables ) {


		EmpiricalMiComputerForBinaryDataStep computer =
				new EmpiricalMiComputerForBinaryDataStep(_data, Variables);
		ArrayList<double[]> miArray = computer.computerPairwise();

		return  miArray;
	}

	

	
	/**
	 * 
	 * Return the best pair of variables with max MI in _mis.
	 */
	public static void findBestPair(ArrayList<Variable> bestPair,
			Set<Variable> VariablesSet, Map<String, Integer> varId, ArrayList<double[]> mis) {
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
			int iId = varId.get(vi.getName());
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);
				int jId = varId.get(vj.getName());
				double mi = mis.get(iId)[jId];

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
	private static Function computeEmpDist(List<Variable> varPair, DataSet _data, Map<Variable, Map<DataCase, Function>> _latentPosts) {
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
	 * Version:          MI(X, S) = max_{Z \in S} MI(X, Z).
	 * @param mis
	 * @param cluster
	 * @return
	 */
	public static ArrayList<Variable> findShortestOutLink(
			ArrayList<double[]> mis,
			ArrayList<Variable> bestPair, Set<Variable> cluster,
			Set<Variable> VariablesSet,Map<String, Integer> varId,ArrayList<Variable> Variables ) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			boolean a = bestPair == null;
			if (a || !bestPair.contains(inCluster)) {
				
				for(int l = 0; l< mis.get(varId.get(inCluster.getName())).length;l++ ){
				//for (Entry<Integer, Double> entry : mis.get(_varId.get(inCluster.getName())).entrySet()) {
					Variable outCluster =Variables.get(l);
					double mi = mis.get(varId.get(inCluster.getName()))[l];

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

		ArrayList<Variable> ClosestVariablePair = new ArrayList<Variable>();
		ClosestVariablePair.add(bestInCluster);
		ClosestVariablePair.add(bestOutCluster);

		return ClosestVariablePair;
	}

	/**
	 * Find the closest variable to cluster. Note: Never move the bestpair out
	 * Version:            MI(X, S) = \sum_{Z \in S} MI(X, Z)/|S|.

	 * @param mis
	 * @param cluster
	 * @return
	 */
	/*private ArrayList<Variable> findShortestOutLink(
			ArrayList<double[]> mis,
			ArrayList<Variable> bestPair, Set<Variable> cluster,
			Set<Variable> VariablesSet) {
		
		Variable bestOutCluster = null;
		Variable bestInCluster = null;
		boolean a = bestPair == null;
		double maxMI_sum = Double.NEGATIVE_INFINITY;
		for(int l = 0; l< mis.get(1).length;l++ ){
			
			Variable outCluster =_Variables.get(l);
			if (cluster.contains(outCluster)
					|| !(VariablesSet.contains(outCluster))) {
				continue;
			}
			double maxMI = Double.NEGATIVE_INFINITY;
			double mi_sum = 0;
			Variable bestIn = null;
			for(Variable inCluster : cluster){
				double mi =  mis.get(_varId.get(inCluster.getName()))[l];
				mi_sum  = mi_sum + mi;
				if (a || !bestPair.contains(inCluster)){
					if(mi > maxMI){
						maxMI = mi;
						bestIn = inCluster;
					}
				}
                     
			}
			
			
			if (mi_sum > maxMI_sum) {
				maxMI_sum = mi_sum;
				bestOutCluster = outCluster;
				bestInCluster = bestIn;
			}	
		}
			
		ArrayList<Variable> ClosestVariablePair = new ArrayList<Variable>();
		ClosestVariablePair.add(bestInCluster);
		ClosestVariablePair.add(bestOutCluster);

		return ClosestVariablePair;
	}*/


	/**
	 * Stack the results
	 * @param _data
	 */
private LTM BuildHierarchy(LTM OldModel, LTM tree) {
		
		LTM CurrentModel = new LTM(); // the new model to be made
		if (OldModel == null) { // if no old model then just return the curretn model
			return tree;
		}
		
		CurrentModel = OldModel; // build upon the OldModel

		
		Set<Edge> edgeSet = new HashSet<Edge>(); // the edges to remove from the CurrentModel)(OldModel)
		// find those edges
		for (Edge e : OldModel.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			if (tree.getNode(head) != null && tree.getNode(tail) != null) {
				edgeSet.add(e);
			}
		}

		// remove them
		for (Edge e : edgeSet) {
			CurrentModel.removeEdge(e);
		}
		
		// add latent varibles of the new layer to the Current Model
		
		for (Variable v : tree.getInternalVars()) {
			CurrentModel.addNode(v);
		}
		// then add their edges
		for (Edge e : tree.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			CurrentModel.addEdge(CurrentModel.getNodeByName(head),
					CurrentModel.getNodeByName(tail));
		}

	

		// set their CPT in the Current Model, 
		for(AbstractNode nd: tree.getNodes()){
			BeliefNode bnd  = (BeliefNode)nd;
			if(!bnd.isRoot()){
				
			ArrayList<Variable> pair = new ArrayList<Variable>();
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getVariable());
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getParent().getVariable());

			double[] Cpt = bnd.getCpt().getCells();
		
			CurrentModel.getNodeByName(nd.getName()).getCpt().setCells(pair,Cpt);
			}
			// if the node is root then it has no parent so just get its CPT
			else {
				CurrentModel.getNodeByName(nd.getName()).setCpt(
						bnd.getCpt().clone());
			}
		}

		return CurrentModel;
	}	
	
	

	
	 private double evaluate(BayesNet _modelEst2){ 
	 double Loglikelihood=
	  ScoreCalculator.computeLoglikelihood((BayesNet)_modelEst2, _test); 
	 double perLL = Loglikelihood/_test.getTotalWeight();
	  System.out.println("Per-document log-likelihood =  "+perLL);
	 return perLL;
	 }


	

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
			if (list.size() > 3) {
				for (int Id = 0; Id < 3; Id++) {
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

					for (int j = 1; j < dist.length; j++) {
						severity[i] += Math.log(j * dist[j]);
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
			 
			/*if (severity[0] - severity[1] > 0.01) {

				order[0] = 1;
				order[1] = 0;

				double tmpReal = severity[0];
				severity[0] = severity[1];
				severity[1] = tmpReal;
			}*/
		
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


	

	
	/**
	 * Regular way of smoothing
	 * 
	 */
	private LTM smoothingParameters(LTM model)
	{
		for(AbstractNode node : model.getNodes())
		{
			Function fun = ((BeliefNode)node).getCpt();
					
			for(int i=0; i<fun.getDomainSize(); i++)
			{
				fun.getCells()[i] = (fun.getCells()[i]*_OrigDenseData.getTotalWeight()+1)/(_OrigDenseData.getTotalWeight()+ ((BeliefNode)node).getVariable().getCardinality());
			}
		}
		return model;
	}
	
	
	/**
	 * Update the collections of P(Y|d). Specifically, remove the entries for
	 * all the latent variables in the given sub-model except the root, and
	 * compute P(Y|d) for the latent variable at the root and each data case d.
	 */
	public static void updateStats(LTM subModel, DataSet _data, Map<Variable, Map<DataCase, Function>> _latentPosts) {
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
	
	public static UndirectedGraph learnMaximumSpanningTree(
			Map<Variable, LTM> hierarchies, DataSet _data, Map<Variable, Map<DataCase, Function>> _latentPosts) {
		// initialize the data structure for pairwise MI
		List<StringPair> pairs = new ArrayList<StringPair>();

		// the collection of latent variables.
		List<Variable> vars = new ArrayList<Variable>(hierarchies.keySet());

		List<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		int nVars = vars.size();
		
		// Can make thi parallel
		// enumerate all pairs of latent variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair,_data,_latentPosts);
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
	
	public static DataSet HardAssignment(LTM CurrentModel, LTM latentTree, DataSet _OrigDenseData) {
		ArrayList<DataCase> data = _OrigDenseData.getData();

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
			ctp.setEvidence(_OrigDenseData.getVariables(), states);
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

