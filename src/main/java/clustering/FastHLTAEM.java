package clustering;
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
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.la4j.decomposition.MatrixDecompositor;
import org.la4j.inversion.MatrixInverter;
import org.la4j.matrix.Matrices;
import org.la4j.matrix.Matrix;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.la4j.vector.Vector;
import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.UndirectedGraph;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.learner.EmLearner;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.DataSetLoader;
import org.latlab.util.Function;
import org.latlab.util.ScoreCalculator;
import org.latlab.util.StringPair;
import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.latlab.util.DataSet.DataCase;
import org.la4j.LinearAlgebra;
import java.util.Random;
import java.lang.Object;


public class FastHLTAEM {

	/**
	 * @param args
	 */
	/**
	 * The collection of pairwise mutual information. 
	 */
	
//	private LTM _model;
	private BayesNet _modelEst;
	
	/**
	 * Original data.
	 */
	private static DataSet _Origdata;
	private DataSet _test;
	
	
	private Set<Variable> _InternalNodeNeedRemove= new HashSet<Variable>();

	
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
	 * The collection of hierarchies. Each hierarchy represents a LCM and
	 * is indexed by the variable at its root.
	 */
	private Map<Variable, LTM> _hierarchies;
	
	/**
	 * The ArrayList of manifest variables with orders.
	 */
	private ArrayList<Variable> _Variables = new ArrayList<Variable>();
	
	/**
	 * The collection of manifest variables that wait to do UD-test.
	 */
	private Set<Variable> _VariablesSet = new HashSet<Variable>();

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
	 * Maximum number of island size
	 */
    private int _maxIsland;
	/**
	 * The collection of pairwise mutual information. 
	 */
	private Map<Variable, Map<Variable, Double>> _mis;
	
	/**
	 * whether true class labels are included in data file.
	 */
	boolean _noLabel = false;
	/**
	 * Save bestPair of observed variables for every latent variable(LCM)
	 */
	Map<String, ArrayList<Variable>> _bestpairs = new HashMap<String, ArrayList<Variable>>();

	
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
	 * Main Method
	 * @param args
	 * @throws Throwable
	 */
	
	public static void main(String[] args) throws Exception {
		if (args.length <8) {
			System.err.println("Usage: java FastHLTA model originaldata testdata EmMaxSteps EmNumRestarts EM-threshold UDtest-threshold");
			System.exit(1);
		}
		// TODO Auto-generated method stub
		
		FastHLTAEM Fast_learner =new FastHLTAEM();
		Fast_learner.initialize(args);
		long start = System.currentTimeMillis();
		Fast_learner.IntegratedLearn();
		System.out.println("--- Total Time: " + (System.currentTimeMillis() - start) + " ms ---");
		// When there is a true model where data are sampled from, we also evaluate the loglikelihood of test set on true model
//		Fast_learner.evaluateTrue();
    	//Fast_learner.createdata(args);
		}
         
	
	
	/**
	 * Initialize All
	 * @param args
	 * @throws IOException
	 * @throws Exception
	 */
	public void initialize(String[] args) throws IOException, Exception

	{


	//True model, just use any model because now it is useless but I don't want to modify the args[] input.

//	_model = new LTM();
//
//	Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
//
//	parser.parse(_model);



	//Read the data set

	_Origdata = new DataSet(DataSetLoader.convert(args[1]));

	      for(Variable a : _Origdata.getVariables()){

	    	_VariablesSet.add(a);      

	     _Variables.add(a);

	  }   

	// if there is no test set just use training data set  as test data

	    //_test = new DataSet(DataSetLoader.convert(args[2]));

	   

	    _EmMaxSteps  = Integer.parseInt(args[3]);

	    _EmNumRestarts=Integer.parseInt(args[4]); 

	    _emThreshold = Double.parseDouble(args[5]); 

	    _UDthreshold=Double.parseDouble(args[6]);
	    
	    _modelname = args[7];

	    _maxIsland = Integer.parseInt(args[8]);
	    _maxTop    = Integer.parseInt(args[9]);
	}           
	

    public void IntegratedLearn(){
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
     *  Build the whole HLTA layer by layer
     * @return BayesNet HLTA
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    public BayesNet FastHLTA_learn() throws FileNotFoundException, UnsupportedEncodingException 
	{
		
	    DataSet data = _Origdata;
		BayesNet CurrentModel = null;

		int level = 2;
		while (true) {
			BayesNet Alayer = FastLTA_flat(data, level);

			CurrentModel = BuildHierarchy(CurrentModel, Alayer);

		//	if (CurrentModel != null)
		//		CurrentModel.saveAsBif("Hierarchical-LTM-Level-" + (level - 1) + ".bif");

			if (Alayer.getInternalVars().size() <= _maxTop)
				break;
           
			data = HardAssignment(CurrentModel, Alayer);

			level++;
		}
		
		//rename latent variables, reorder the states.
		CurrentModel = postProcessingModel(CurrentModel);
		
		//output final model.
		CurrentModel.saveAsBif(_modelname+".bif");

		double score = ScoreCalculator.computeBic(CurrentModel, _Origdata);
		System.out.println("BIC Score on the training data: " + score);

		return CurrentModel;
	}
    
    /**
     * Build one layer of LTA
     * @param _data
     * @param Level
     * @return
     */
   /* public BayesNet FastLTA_flat(DataSet _data, int Level){
    	
		int i = 1;
		initialize(_data);
		// Call lcmLearner iteratively and learn the LCMs.
		while (!isDone()) 
		{		
			System.out.println("======================= Learn Island : "+ i + " , number of variables left: "+ _VariablesSet.size()+"  =================================");
			if(_VariablesSet.size()==3){
				if(_mis.isEmpty())
				{
					    ArrayList<Variable> bestPair = new ArrayList<Variable>();
					    // compute MI and find the pair with the largest MI value
						long startMI = System.currentTimeMillis();
							_mis = computeMis(bestPair, _data);
						System.out.println("======================= _mis has been calculated  =================================");
						System.out.println("--- ComputingMI Time: " + (System.currentTimeMillis() - startMI) + " ms ---");

				}
	    		DataSet data_proj = _data.project(new ArrayList<Variable>(_VariablesSet),false);
	    		LTM subModel = MmLCM_learner(_VariablesSet, null, data_proj);
				updateHierarchies(subModel);
				updateVariablesSet(subModel);
	    		break;
			}
			
		
			ArrayList<Variable> bestPair = new ArrayList<Variable>();
			//_mis only needs to compute once
			
			if(_mis.isEmpty())
			{
				    // compute MI and find the pair with the largest MI value
					long startMI = System.currentTimeMillis();
						_mis = computeMis(bestPair, _data);
					System.out.println("======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: " + (System.currentTimeMillis() - startMI) + " ms ---");

					
			}else{
					findBestPair(bestPair, _VariablesSet);
			}
		
			Set<Variable> cluster = new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 3 variables now 
			ArrayList<Variable> ClosestVariablePair = findShortestOutLink(_mis, cluster,_VariablesSet);

			LTM subModel = null;
			if (!ClosestVariablePair.isEmpty()) {
				cluster.addAll(ClosestVariablePair);
			}
			
			while(true){
			
				ClosestVariablePair = findShortestOutLink(_mis, cluster, _VariablesSet);
				cluster.addAll(ClosestVariablePair);
	    		DataSet data_proj2l = _data.project(new ArrayList<Variable>(cluster),false);
				LTM m_uniq = MmLCM_learner(cluster, null, data_proj2l);
				//Obtain m_1,m_2,m_all from 
				ArrayList<LTM> m = MmLTM_2L_learner(cluster,ClosestVariablePair, data_proj2l);
          
				double mulModelBIC = ScoreCalculator.computeBic(m.get(2), _data);
				double uniModelBIC = ScoreCalculator.computeBic(m_uniq, _data);

				if (mulModelBIC - uniModelBIC > _UDthreshold){ 
					if(_VariablesSet.size()-cluster.size()==0){
						subModel = m.get(0);
						updateHierarchies(subModel);				updateVariablesSet(subModel);
                        subModel = m.get(1);
                        updateHierarchies(subModel);				updateVariablesSet(subModel);     
					break;
					}else{
						subModel  = m.get(0);;
                        updateHierarchies(subModel);				updateVariablesSet(subModel); break;}    
				}else if(_VariablesSet.size()-cluster.size()==0||(cluster.size()>=_maxIsland&&(_VariablesSet.size()-cluster.size())>=3)){
					    subModel = m_uniq;
                        updateHierarchies(subModel);				updateVariablesSet(subModel);    
                        break;
				}
			}
			
            
			i++;
		}
		
		
		//build the whole latent tree.
		long BuildTime = System.currentTimeMillis();
		LTM latentTree = BuildLatentTree(_data);
		System.out.println("Building Tree Time: " + (System.currentTimeMillis() - BuildTime) + " ms ---");

		
		//check the positivity
		
		BayesNet BNlatentTree = CheckPos(latentTree,_data);
		
		return BNlatentTree;	
    }*/

 public BayesNet FastLTA_flat(DataSet _data, int Level){
    	
		int i = 1;
		initialize(_data);
		// Call lcmLearner iteratively and learn the LCMs.
		while (!isDone()) 
		{		
			System.out.println("======================= Learn Island : "+ i + " , number of variables left: "+ _VariablesSet.size()+"  =================================");
			if(_VariablesSet.size()==3){
				if(_mis.isEmpty())
				{
					    ArrayList<Variable> bestPair = new ArrayList<Variable>();
					    // compute MI and find the pair with the largest MI value
						long startMI = System.currentTimeMillis();
							_mis = computeMis(bestPair, _data);
						System.out.println("======================= _mis has been calculated  =================================");
						System.out.println("--- ComputingMI Time: " + (System.currentTimeMillis() - startMI) + " ms ---");

				}
				ArrayList<Variable> bestP = new ArrayList<Variable>();
				findBestPair(bestP, _VariablesSet);
				ArrayList<Variable> Varstemp = new ArrayList<Variable>(_VariablesSet);
	    		DataSet data_proj = _data.project(Varstemp,false);
	    		LTM subModel = LCM3N(Varstemp, data_proj);
				updateHierarchies(subModel,bestP);
				updateVariablesSet(subModel);
	    		break;
			}
			
		
			ArrayList<Variable> bestPair = new ArrayList<Variable>();
			//_mis only needs to compute once
			
			if(_mis.isEmpty())
			{
				    // compute MI and find the pair with the largest MI value
					long startMI = System.currentTimeMillis();
						_mis = computeMis(bestPair, _data);
					System.out.println("======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: " + (System.currentTimeMillis() - startMI) + " ms ---");

					
			}else{
					findBestPair(bestPair, _VariablesSet);
			}
		
			Set<Variable> cluster= new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 3 variables now 
			ArrayList<Variable> ClosestVariablePair = findShortestOutLink(_mis, null, cluster, _VariablesSet);
			ArrayList<Variable> cluster_3n = new ArrayList<Variable>(bestPair);

			//cluster_3n is  an array containing 3 variables : bestpair and ClosestVariablePair.get(1)
			LTM subModel = null;
			if (!ClosestVariablePair.isEmpty()) {
				cluster_3n.add(ClosestVariablePair.get(1));
				cluster.add(ClosestVariablePair.get(1));
			}
			//m0
    		LTM m0 = LCM3N(cluster_3n, _data.project(cluster_3n));
            //cluster is the working set
			while(true){
				ClosestVariablePair = findShortestOutLink(_mis, bestPair, cluster, _VariablesSet);
				cluster.add(ClosestVariablePair.get(1));
	    		DataSet data_proj2l = _data.project(new ArrayList<Variable>(cluster));
				LTM m1 = EmLCM_learner(m0, ClosestVariablePair.get(1),bestPair, data_proj2l);
				LTM minput = m1.clone();
				LTM m2 = EmLTM_2L_learner(minput,bestPair,ClosestVariablePair, data_proj2l);
                m0 = m1.clone();
				double mulModelBIC = ScoreCalculator.computeBic(m2, data_proj2l);
				double uniModelBIC = ScoreCalculator.computeBic(m1, data_proj2l);

				if (mulModelBIC - uniModelBIC > _UDthreshold){ 
					if(_VariablesSet.size()-cluster.size()==0){
						//split m2 to 2 LCMs subModel1 and subModel2
						LTM subModel1 = m1.clone();
						for(int id=0;id<2;id++){
							Edge e = subModel1.getNode(ClosestVariablePair.get(id)).getEdge(subModel1.getRoot());
					    	//Should remove node first then edge.
							subModel1.removeNode(subModel1.getNode(ClosestVariablePair.get(id)));
							subModel1.removeEdge(e);
						}
						// To get subModel2
						HashSet<String> donotUpdate = new HashSet<String>();
						//learn an LCM with ClosestVariablePair and any other one node
						LTM subModel2 = new LTM();
						ArrayList<Variable> cluster_sub2_3node = new ArrayList<Variable>(ClosestVariablePair);
						cluster_sub2_3node.add(bestPair.get(1));
						subModel2 = LTM.createLCM(cluster_sub2_3node, 2);
						//copy parameters from m2 to submodel2
						ArrayList<Variable> var2s = new ArrayList<Variable>(subModel2.getNode(ClosestVariablePair.get(0)).getCpt().getVariables());
				 		subModel2.getNode(ClosestVariablePair.get(0)).getCpt().setCells(var2s, m2.getNode(ClosestVariablePair.get(0)).getCpt().getCells());
				 		var2s = new ArrayList<Variable>(subModel2.getNode(ClosestVariablePair.get(1)).getCpt().getVariables());
				 		subModel2.getNode(ClosestVariablePair.get(1)).getCpt().setCells(var2s, m2.getNode(ClosestVariablePair.get(1)).getCpt().getCells());
						donotUpdate.add(ClosestVariablePair.get(0).getName());
						donotUpdate.add(ClosestVariablePair.get(1).getName());
						
						ParallelEmLearner emLearner = new ParallelEmLearner();
						emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
						emLearner.setMaxNumberOfSteps(_EmMaxSteps);
						emLearner.setNumberOfRestarts(_EmNumRestarts);
						emLearner.setReuseFlag(false);
						emLearner.setThreshold(_emThreshold);
				        emLearner.setDontUpdateNodes(donotUpdate);
						subModel2 = (LTM)emLearner.em(subModel2, data_proj2l.project(cluster_sub2_3node));
						
						//remove the edge of other node
						Edge e2 = subModel2.getNode(bestPair.get(1)).getEdge(subModel2.getRoot());
						subModel2.removeNode(subModel2.getNode(bestPair.get(1)));
						subModel2.removeEdge(e2);
						
						updateHierarchies(subModel1, bestPair);				updateVariablesSet(subModel1);
                        updateHierarchies(subModel2, ClosestVariablePair);				updateVariablesSet(subModel2);     
					break;
					}else{
						for(int id=0;id<2;id++){
							Edge e = m1.getNode(ClosestVariablePair.get(id)).getEdge(m1.getRoot());
					    	//Should remove node first then edge.
							m1.removeNode(m1.getNode(ClosestVariablePair.get(id)));
							m1.removeEdge(e);
						}
                        updateHierarchies(m1, bestPair);				updateVariablesSet(m1); break;}    
				}else if(_VariablesSet.size()-cluster.size()==0||(cluster.size()>=_maxIsland&&(_VariablesSet.size()-cluster.size())>=3)){
					    subModel = m1;
                        updateHierarchies(subModel, bestPair);				updateVariablesSet(subModel);    
                        break;
				}
			}
			i++;
		}
	
		
		//build the whole latent tree.
		long BuildTime = System.currentTimeMillis();
		LTM latentTree = BuildLatentTree(_data);
		System.out.println("Building Tree Time: " + (System.currentTimeMillis() - BuildTime) + " ms ---");

		
		//check the positivity
		
		BayesNet BNlatentTree = CheckPos(latentTree,_data);
		
		return BNlatentTree;	
    }
    
    
 
    /**
     * 
     */
 	private LTM LCM3N(ArrayList<Variable> variables3, DataSet data_proj){
 		LTM LCM_new = LTM.createLCM(variables3, 2);
		LCM_new.randomlyParameterize();	
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);

		LCM_new = (LTM)emLearner.em(LCM_new, data_proj.project(variables3));
 		
		return LCM_new;
 	}
 	
 	
 	private LTM EmLCM_learner(LTM modelold, Variable x, ArrayList<Variable> bestPair, DataSet data_proj){
		
 		ArrayList<Variable> cluster3node = new ArrayList<Variable>(bestPair);
 		cluster3node.add(x);
 		//Learn a 3node LTM : bestpair and newly added node
 		LTM LCM3var = LTM.createLCM(cluster3node, 2);
 		LCM3var.randomlyParameterize();	
		HashSet<String> donotUpdate = new HashSet<String>();

 		
 		ArrayList<Variable> var2s = new ArrayList<Variable>(LCM3var.getNode(bestPair.get(0)).getCpt().getVariables());
 		LCM3var.getNode(bestPair.get(0)).getCpt().setCells(var2s, modelold.getNode(bestPair.get(0)).getCpt().getCells());
		donotUpdate.add(bestPair.get(0).getName());
		var2s = new ArrayList<Variable>(LCM3var.getNode(bestPair.get(1)).getCpt().getVariables());
 		LCM3var.getNode(bestPair.get(1)).getCpt().setCells(var2s, modelold.getNode(bestPair.get(1)).getCpt().getCells());
		donotUpdate.add(bestPair.get(1).getName());
		var2s = new ArrayList<Variable>(LCM3var.getRoot().getCpt().getVariables());
 		LCM3var.getRoot().getCpt().setCells(var2s, modelold.getRoot().getCpt().getCells());
		donotUpdate.add(LCM3var.getRoot().getName());
		
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
        emLearner.setDontUpdateNodes(donotUpdate);
		LCM3var = (LTM)emLearner.em(LCM3var, data_proj.project(cluster3node));
 		
 		LTM uniModel = modelold.clone();
 		
 		uniModel.addNode(x);
 		
 		uniModel.addEdge(uniModel.getNode(x), uniModel.getRoot());
 		ArrayList<Variable> vars = new ArrayList<Variable>(uniModel.getNode(x).getCpt().getVariables());
 		uniModel.getNode(x).getCpt().setCells(vars, LCM3var.getNode(x).getCpt().getCells());
 		
 		return uniModel;
 	}
 	
 	/**
 	 * 
 	 * @param unimodel
 	 * @param bestPair
 	 * @param ClosestPair
 	 * @param data_proj
 	 * @return a model with two latent varaibles (without node relocation step)
 	 */
 	
 	
 		private LTM EmLTM_2L_learner(LTM unimodel,ArrayList<Variable> bestPair, ArrayList<Variable> ClosestPair, DataSet data_proj){
		
		ArrayList<Variable> cluster2BeAdded = new ArrayList<Variable>(unimodel.getManifestVars());
		ArrayList<Variable> cluster4var = new ArrayList<Variable>(bestPair);
		
		//construct a LTM with 4 observed variables 2 latent variables
		LTM lCM = new LTM();
		BeliefNode h1 = lCM.addNode(new Variable(2));
		BeliefNode h2 = lCM.addNode(new Variable(2));
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
		
		//copy parameters of unimodel to m1
		HashSet<String> donotUpdate = new HashSet<String>();
		ArrayList<Variable> var1 = new ArrayList<Variable>(lCM.getRoot().getCpt().getVariables());
		lCM.getRoot().getCpt().setCells(var1,unimodel.getRoot().getCpt().getCells());		
		
		ArrayList<Variable> var2s = new ArrayList<Variable>(lCM.getNode(bestPair.get(0)).getCpt().getVariables());
        lCM.getNode(bestPair.get(0)).getCpt().setCells(var2s, unimodel.getNode(bestPair.get(0)).getCpt().getCells());
    	var2s = new ArrayList<Variable>(lCM.getNode(bestPair.get(1)).getCpt().getVariables());
        lCM.getNode(bestPair.get(1)).getCpt().setCells(var2s, unimodel.getNode(bestPair.get(1)).getCpt().getCells());
		
        
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
	//	System.out.println("--- Total Time for checking (EM): " + (System.currentTimeMillis() - startcheck) + " ms ---");
		
		//Add the rest of variables to m1 and copy parameters
		LTM multimodel = LTM4var.clone(); 
		for(Variable v: cluster2BeAdded){

			multimodel.addEdge(multimodel.addNode(v), multimodel.getRoot());
			var2s = new ArrayList<Variable>(multimodel.getNode(v).getCpt().getVariables());
			multimodel.getNode(v).getCpt().setCells(var2s,unimodel.getNode(v).getCpt().getCells());		
		}
 
		   
		return multimodel;
	}
    /**
     * Final check the positivity of obtained ctps at the end of learning each layer
     * We can try: 1, set the negative ones to a small positive value and normalize the matrix
     *             2, re-do EM on those nodes containing negative ctp
     */
    private BayesNet CheckPos(LTM latentTree, DataSet _data) {
		// TODO Auto-generated method stub
    	
    	HashSet<String> donotUpdate = new HashSet<String>();
		for (AbstractNode node : latentTree.getNodes()) {
			Function fun = latentTree.getNodeByName(node.getName()).getCpt();
			if (fun.min()<0) {
				System.out.println("Node "+node.getName()+" needs to be recomputed"+" "+ fun.getCells()[0]+" "+ fun.getCells()[1]+" "+ fun.getCells()[2]+" "+ fun.getCells()[3]);
				/*Matrix  mat = FunToMat(latentTree.getNodeByName(node.getName()));
				mat = checkMatNeg(mat);
				setVdis(latentTree.getNodeByName(node.getName()).getVariable(), mat, latentTree);*/
			}else{
				donotUpdate.add(node.getName());
			}
		}


        if(!(donotUpdate.size() ==latentTree.getNodes().size())){
		ParallelEmLearner FinalemLearner = new ParallelEmLearner();
		FinalemLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		FinalemLearner.setMaxNumberOfSteps(_EmMaxSteps);
		FinalemLearner.setNumberOfRestarts(_EmNumRestarts);
		FinalemLearner.setReuseFlag(false);
		FinalemLearner.setThreshold(_emThreshold);
		FinalemLearner.setDontUpdateNodes(donotUpdate);

		long startcheck = System.currentTimeMillis();
		latentTree = (LTM) FinalemLearner.em(latentTree, _data);
		System.out.println("--- Total Time for checking (EM): " + (System.currentTimeMillis() - startcheck) + " ms ---");
        }
		
		// BayesNet BNlatentTree = Ltm2Bn(latentTree);
       LTM latentTree_smoothed = smoothingParameters(latentTree);
       BayesNet BNlatentTree = Ltm2Bn(latentTree_smoothed);
		
		return BNlatentTree;
	}

    
    /**
     * LTM to Bayesnet
     * @param model
     * @return
     */
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
			bNode.setCpt(model.getNode(bNode.getVariable()).getCpt().clone());
		}

		return copy;
	}


    /**
     * Learn a LTM with exaclty 2 latent variables
     * @param cluster
     * @param closepair
     * @param data_proj
     * @return
     */

/*	public ArrayList<LTM> MmLTM_2L_learner(Set<Variable> cluster, ArrayList<Variable> closepair, DataSet data_proj){
		
    	//obtain m1
    	Set<Variable> cluster1 = new HashSet<Variable>(cluster);
    	cluster1.remove(closepair.get(1));//remove the 
    	LTM m1 = MmLCM_learner(cluster1,closepair.get(0), data_proj);
    	
    	// Remove link to the close pair
    	Edge e1 = m1.getNode(closepair.get(0)).getEdge(m1.getRoot());
    	//Should remove node first then edge.
    	m1.removeNode(m1.getNode(closepair.get(0)));
    	m1.removeEdge(e1);

    	//obtain m2
    	Set<Variable> cluster2 = new HashSet<Variable>(closepair);
    	Variable NodeAdded = m1.getNodeByName(_refNodes.get(m1.getRoot().getName())).getVariable();
    	cluster2.add(NodeAdded);
    	LTM m2 = MmLCM_learner(cluster2, NodeAdded, data_proj);
    	
    	
    	// Remove link to the close pair
    	Edge e2 = m2.getNode(NodeAdded).getEdge(m2.getRoot());
    	m2.removeNode(m2.getNode(NodeAdded));
    	m2.removeEdge(e2);

    	// link m1 m2 and update the conditional probabilities
    	
    	LTM M2tree = m1.clone();

		// Construct tree: first, add all manifest nodes and latent nodes.
		// Second, copy the edges and CPTs in each LCMs.

			for (AbstractNode node : m2.getNodes()) {
				M2tree.addNode(((BeliefNode) node).getVariable());
			}

			// copy the edges and CPTs
			for (AbstractNode node : m2.getNodes()) {
				BeliefNode bNode = (BeliefNode) node;

				if (!bNode.isRoot()) {
					BeliefNode parent = (BeliefNode) bNode.getParent();

					BeliefNode newNode =
							M2tree.getNode(bNode.getVariable());
					BeliefNode newParent =
							M2tree.getNode(parent.getVariable());

					M2tree.addEdge(newNode, newParent);
					newNode.setCpt(bNode.getCpt().clone()); // copy the parameters of manifest variables
				}
			}
		    
			M2tree.addEdge(M2tree.getNodeByName(m2.getRoot().getName()), M2tree.getNodeByName(m1.getRoot().getName()));
			

				// ref_node: R, par(R)= par(v).Obtain the P(R|par(R))
			   String ref_node = _refNodes.get(m1.getRoot().getName());
			   Matrix P_refgivPar = FunToMat(m1.getNodeByName(ref_node));
			   
			   String sef_node  = _refNodes.get(m2.getRoot().getName());
			   Matrix P_sefgivL = FunToMat(m2.getNodeByName(sef_node));
			   
			   //Calculate the joint distribution
			   ArrayList<Variable> Temp_AF = new ArrayList<Variable>();
			   Temp_AF.add(M2tree.getNodeByName(sef_node).getVariable());
			   Temp_AF.add(M2tree.getNodeByName(ref_node).getVariable());
			   DataSet data_AF = data_proj.project(Temp_AF,false);
			   Matrix P_AF = computeJointDist(Temp_AF, data_AF);
			   
			   //P_{A|par(L)} = Normalize{P_AF *P_{F|par(L)}^{-1}.transpose}
	  	  	   MatrixInverter inverter_refgivPar = P_refgivPar.withInverter(LinearAlgebra.GAUSS_JORDAN);
	  		   Matrix P_sefgivPar = Normalize_Col(P_AF.multiply( inverter_refgivPar.inverse(LinearAlgebra.DENSE_FACTORY).transpose()));
	  		   //P_{A|L}.inverse
	  		    MatrixInverter inverter_sefgivL = P_sefgivL.withInverter(LinearAlgebra.GAUSS_JORDAN);
			    Matrix P_sefgivL_inv = inverter_sefgivL.inverse(LinearAlgebra.DENSE_FACTORY);
	  		

	  	  	    //P_{L|par(L)}= P_{A|L}.inv * P_{A|par(L)}
	  	  	    Matrix P_cond = P_sefgivL_inv.multiply(P_sefgivPar);
	           // P_cond= Normalize_Col(checkMatNeg(P_cond));

				setVdis(m2.getRoot().getVariable(), P_cond, M2tree);   
               
				ArrayList<LTM> LTMgroup = new ArrayList<LTM>(3);
				LTMgroup.add(m1); LTMgroup.add(m2); LTMgroup.add(M2tree);


                return LTMgroup;				
				
				
    
    }
    
    */
    
    
    /**
     * Build an LCM and return it with parameters
     * @param cluster All the observed nodes in this LCM
     * @param A : just for indicate which one is the reference node that should 
     * not belong to this LCM but added for parameter estimation
     * @return
     */
    
    /*
    public LTM MmLCM_learner(Set<Variable> cluster, Variable other, DataSet data_proj){
    	ArrayList<Variable> Cluster = new ArrayList<Variable>();
    	Cluster.addAll(cluster);
    	// Create a LCM with given observed variables
    	LTM LCM_new = LTM.createLCM(Cluster, 2);
		LCM_new.randomlyParameterize();	
		    	
    	//Find out AC with Highest MI
    	ArrayList<Variable> AC_array = new ArrayList<Variable>();
		findBestPair(AC_array, cluster);		
		Set<Variable> AC_set = new HashSet<Variable>();
		AC_set.addAll(AC_array);
		Variable A = AC_array.get(0);
		//find out the B
		ArrayList<Variable> AB_array = new ArrayList<Variable>();
		AB_array = findShortestOutLink(_mis,null, AC_set, cluster);
		
		//Get P_AbC x P_AC and do Eigen-decomposition to have P_A|H
		ArrayList<Variable> AbC = new ArrayList<Variable>(AC_array);
		AbC.add(1, AB_array.get(1));
		Matrix P_AC = computeJointDist(AC_array, data_proj.project(AC_array,false));
		if(P_AC.determinant()>1e-6||P_AC.determinant()<-1e-6){
				Matrix P_AgivH = EqDone(AbC, AC_array, data_proj);
				//P_AgivH = Normalize_Col(checkMatNeg(P_AgivH));
				if(P_AgivH.determinant()>1e-6||P_AgivH.determinant()<-1e-6){
			        setVdis(A, P_AgivH, LCM_new);
			        MatrixInverter inverter_AgivH = P_AgivH.withInverter(LinearAlgebra.GAUSS_JORDAN);
			  	    Matrix P_AgivH_inverse = inverter_AgivH.inverse(LinearAlgebra.DENSE_FACTORY);
				//Calculate P_AX
			    	ArrayList<Variable> Temp_AX = new ArrayList<Variable>();
			    	cluster.remove(A);
			    	Temp_AX.add(A);
			    	boolean flag  = false;
					for(Variable v : cluster){
						Temp_AX.add(v);
						Matrix P_AX = computeJointDist(Temp_AX, data_proj.project(Temp_AX,false));
				  	    Matrix P_HX = P_AgivH_inverse.multiply(P_AX);
				  	    //B check it
				  	    Matrix P_XgivH =Normalize_Col(P_HX.transpose());
				  	    //P_XgivH = Normalize_Col(checkMatNeg(P_XgivH));
			            setVdis(v, P_XgivH, LCM_new);
				    if(!flag){
			  	    	Matrix P_H = new  Basic2DMatrix(new double[][]{
			  					{0, 0}});
			  	 
			  	    	P_H.set(0, 0, P_HX.transpose().getColumn(0).sum());
			  	    	P_H.set(0, 1, P_HX.transpose().getColumn(1).sum());
			  	    	P_H = Normalize_Row(P_H);
			  	    	setRootdis(P_H, LCM_new);
			  	    	flag =true;
			  	    	
				  	    }
				    Temp_AX.remove(v);
				  	   
					}
					cluster.add(A);
				}else{
					System.out.println("P_AgivH is not invertible");
					ParallelEmLearner emLearner = new ParallelEmLearner();
					emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
					emLearner.setMaxNumberOfSteps(30);
					emLearner.setNumberOfRestarts(3);
					emLearner.setReuseFlag(false);
					emLearner.setThreshold(0.01);
	
					LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(Cluster, false));
				}
		}else{
			System.out.println("P_AC is not invertible");
			ParallelEmLearner emLearner = new ParallelEmLearner();
			emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
			emLearner.setMaxNumberOfSteps(30);
			emLearner.setNumberOfRestarts(3);
			emLearner.setReuseFlag(false);
			emLearner.setThreshold(0.01);

			LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(Cluster, false));	
		}
		
		/*if(other==null || (!A.equals(other))){
			_refNodes.put(LCM_new.getRoot().getName(), A.getName());

		}else{
			_refNodes.put(LCM_new.getRoot().getName(), AC_array.get(1).getName());

		}*/
/*		for(Variable no: LCM_new.getManifestVars()){
			Matrix temp = FunToMat(LCM_new.getNode(no));
			if((temp.determinant()>1e-6||temp.determinant()<-1e-6)&&(other==null||(!no.equals(other)))){
				_refNodes.put(LCM_new.getRoot().getName(), no.getName());
                break;
			}
		}
		if(!_refNodes.containsKey(LCM_new.getRoot().getName())){
			LCM_new =  smoothingLTM(LCM_new);
			_refNodes.put(LCM_new.getRoot().getName(), A.getName());

		}
		return LCM_new;
	}
      
        private Matrix checkMatNeg(Matrix A){
        	
		    
			for(int j=0;j<A.columns();j++){
				if(A.get(0, j)<0){
					if(A.get(0,j)>-0.5){
						A.set(0, j, 0.001);
						A.set(1, j, 0.999);
					}else{
						A.set(0,j,0.999);
						A.set(1,j,0.001);
					}
					
				}
			}
		
        	
        	
        	
			return A;
        	
        }

*/
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
	 * Do the eigen decomposition on the triplet
	 * @param Triplet
	 * @param AC_array
	 * @param data_proj
	 * @return
	 */
	public Matrix EqDone(ArrayList<Variable> Triplet, ArrayList<Variable> AC_array, DataSet data_proj){
	    //Calculate P_AC and P_AC_inv
	    DataSet data_ABC =  data_proj.project(Triplet,false);
	    Matrix P_AC = computeJointDist(AC_array, data_ABC.project(AC_array,false));
	    // We will use Gauss-Jordan method for inverting
	    
		    MatrixInverter inverter_AC = P_AC.withInverter(LinearAlgebra.GAUSS_JORDAN);
		    Matrix P_AC_inv = inverter_AC.inverse(LinearAlgebra.DENSE_FACTORY);

	
	    
	    //Calculate P_AbC
		Matrix P_X0X = new Basic2DMatrix(new double[][]{
				{0, 0},
				{0, 0}});
		Matrix P_X1X = new Basic2DMatrix(new double[][]{
				{0, 0},
				{0, 0}});
		
		Matrix P_temp = new Basic2DMatrix(new double[][]{
				{0, 0},
				{0, 0}});
	  
    	for (DataCase datum :data_ABC.getData()) {
			int[] states = datum.getStates(); 
		if(states[1] == 0){
			P_X0X.set(states[0], states[2], datum.getWeight()+P_X0X.get(states[0], states[2]));
		}
		else if(states[1] == 1){
			P_X1X.set(states[0], states[2], datum.getWeight()+P_X1X.get(states[0], states[2]));
		}
    	}
    	

		double sum_B = data_proj.getTotalWeight();
	    P_X1X = P_X1X.divide(sum_B);  // this is P_A1C
	    
	    // Do the decomposition
		P_temp = P_X1X.multiply(P_AC_inv);
		Matrix[] Results = Eigendecomposite(P_temp);   
		//P_AgivH
		Matrix P_AgivH = Normalize_Col(Results[0]);
		
		return P_AgivH;
	}
		
	
	
	
	/**
	 * Link the islands
	 * @param _data
	 * @return
	 */
	private LTM BuildLatentTree(DataSet _data) {

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
				
				BeliefNode h1 = lTM_4n.addNode(new Variable(2));
				BeliefNode h2 = lTM_4n.addNode(new Variable(2));
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
		

		return latentTree;
	}
    
    
	public class EmpiricalMiComputer {
		private final DataSet data;
		private final List<Variable> variables;
		private final boolean normalize;

		public EmpiricalMiComputer(
				DataSet data, List<Variable> variables, boolean normalize) {
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

			//use normalized version of MI.
			if (normalize) 
			{
				// this version used in Strehl & Ghosh (2002)
				double enti = Utils.computeEntropy(pairDist.sumOut(vj));
				double entj = Utils.computeEntropy(pairDist.sumOut(vi));
				if(mi != 0)
				{
					mi /= Math.sqrt(enti * entj);
				}
			}
			
			return mi;
		}

		/**
		 * Computes a the mutual information between each pair of variables. It does
		 * not contain any valid value on the diagonal.
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

//			private void compute() {
//				computeFirstRange(0, variables.size());
//			}

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
	
	protected Map<Variable, Map<Variable, Double>> computeMis(ArrayList<Variable> bestPair, DataSet _data) {
		return computeMisPar(bestPair, _data);
	}

    
    /**
	 * Compute pairwise MI between the specified list of variable based on the
	 * posterior distributions P(Y|d) and the training data. Also swap the pair
	 * of variables with the largest MI to the head of the list.
	 */
	protected Map<Variable, Map<Variable, Double>> computeMisPar(ArrayList<Variable> bestPair, DataSet _data) 
	{
		// normalize the MI or not
		boolean normalize = false;

		List<Variable> vars = new ArrayList<Variable>(_VariablesSet);
		
		EmpiricalMiComputer computer =
				new EmpiricalMiComputer(_data, vars, normalize);
		double[][] miArray = computer.computerPairwise();
		
		// convert the array to map
		
		// initialize the data structure for pairwise MI
		Map<Variable, Map<Variable, Double>> mis =
				new HashMap<Variable, Map<Variable, Double>>(vars.size());

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		for (int i = 0; i < vars.size(); i++) {
			double[] row = miArray[i];
			
			Map<Variable, Double> map = 
					new HashMap<Variable, Double>(vars.size());
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
			miArray[i] = null;
		}
		
		// set the best pair
		bestPair.add(first);
		bestPair.add(second);

		return mis;
	}

    /**
	 * Compute pairwise MI between the specified list of variable based on the
	 * posterior distributions P(Y|d) and the training data. Also swap the pair
	 * of variables with the largest MI to the head of the list.
	 */
	private Map<Variable, Map<Variable, Double>> computeMisSeq(ArrayList<Variable> bestPair, DataSet _data) 
	{
		// initialize the data structure for pairwise MI
		Map<Variable, Map<Variable, Double>> mis = new HashMap<Variable, Map<Variable, Double>>();
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
		for (int i = 0; i < nVars; i++) 
		{
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) 
			{
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair, _data);				
				double mi = Utils.computeMutualInformation(pairDist);

				//use normalized version of MI.
				if (normalize) 
				{
					// this version used in Strehl & Ghosh (2002)
					double enti = Utils.computeEntropy(pairDist.sumOut(vj));
					double entj = Utils.computeEntropy(pairDist.sumOut(vi));
					if(mi != 0)
					{
						mi /= Math.sqrt(enti * entj);
					}
				}

				// keep both I(vi; vj) and I(vj; vi)
				mis.get(vi).put(vj, mi);
				mis.get(vj).put(vi, mi);

				// update max MI and indices of best pair
				if (mi > maxMi) 
				{
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

    
	

	/**
	 * 
	 * Return the best pair of variables with max MI in _mis.
	 */
	private void findBestPair(ArrayList<Variable> bestPair, Set<Variable> VariablesSet) 
	{
		//Initialize vars as _VarisblesSet
		List<Variable> vars = new ArrayList<Variable>(VariablesSet);
		
		List<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		int nVars = vars.size();

		// enumerate all pairs of variables
		for (int i = 0; i < nVars; i++) 
		{
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) 
			{
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				double mi = _mis.get(vi).get(vj);

				// update max MI and indices of best pair
				if (mi > maxMi) 
				{
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
	private Function computeEmpDist(List<Variable> varPair, DataSet _data) 
	{
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

			//If there is missing data, continue;
			if((viIdx != -1 && states[viIdx]==-1)||(vjIdx != -1 && states[vjIdx]==-1))
			{
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
				freq = freq.times(Function.createIndicatorFunction(vj,
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
			Map<Variable, Map<Variable, Double>> mis, ArrayList<Variable> bestPair, Set<Variable> cluster, Set<Variable> VariablesSet) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			boolean a = bestPair==null;
			if(a||!bestPair.contains(inCluster)){
				for (Entry<Variable, Double> entry : mis.get(inCluster).entrySet()) {
					Variable outCluster = entry.getKey();
					double mi = entry.getValue();
	
					// skip variables already in cluster
					if (cluster.contains(outCluster) || !(VariablesSet.contains(outCluster))) {
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
 
    
    
	
	
	
    
    
    
    
	


	/*
	@SuppressWarnings("unchecked")
	public void doSample(int sampleSize, int SampleNum){
		_Origdata = _model.sample(sampleSize).project(_Variables);
		try {
			_Origdata.save("MC4_"+SampleNum+".txt");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
}*/
	
	
	
	private Matrix FunToMat(BeliefNode nod){

    	Matrix Mat = new Basic2DMatrix(new double[][]{
				{0, 0},
				{0, 0}});
    	
    	Function X = nod.getCpt();
    	if(nod.getVariable().equals(X.getVariables().get(0))){
    		Mat.set(0, 0, X.getCells()[0]);
        	Mat.set(0, 1, X.getCells()[1]);
        	Mat.set(1, 0, X.getCells()[2]);
        	Mat.set(1, 1, X.getCells()[3]);
        	
    	}else{
    		Mat.set(0, 0, X.getCells()[0]);
        	Mat.set(1, 0, X.getCells()[1]);
        	Mat.set(0, 1, X.getCells()[2]);
        	Mat.set(1, 1, X.getCells()[3]);
    	}
    	
    	
    	return Mat;
	}
	
	
/*	private void setVdis(Variable v, Matrix M, LTM tree){
    	
    	ArrayList<Double> cells = new ArrayList<Double>();
    	ArrayList<Variable> pairVar =  new ArrayList<Variable>();
    	pairVar.add(tree.getNode(v).getVariable());
    	pairVar.add(tree.getNode(v).getParent().getVariable());
    

    	cells.add(M.get(0, 0));
    	cells.add(M.get(0, 1));
    	cells.add(M.get(1, 0));
    	cells.add(M.get(1, 1));
    	tree.getNode(v).getCpt().setCells(pairVar,cells);
    	
	}
		
	
	private void setRootdis(Matrix M, LTM tree){
		ArrayList<Double> cells_H = new ArrayList<Double>();
	   	ArrayList<Variable> H =  new ArrayList<Variable>();
	   	H.add(tree.getRoot().getVariable());
	   	cells_H.add(M.get(0, 0));
	   	cells_H.add(M.get(0, 1));
	   	tree.getRoot().getCpt().setCells(H,cells_H);
   }
*/

	public Matrix[] Eigendecomposite(Matrix P){
		Matrix[] Results = null;
		MatrixDecompositor EigenDecompositor = P.withDecompositor(LinearAlgebra.EIGEN);
		Results= EigenDecompositor.decompose(LinearAlgebra.BASIC2D_FACTORY);
		return Results;
	}
	
	
	private Matrix computeJointDist(List<Variable> varPair, DataSet data) 
	{
        
		Matrix P = new Basic2DMatrix(new double[][]{
				{0, 0},
				{0, 0}});
	
		for (DataCase datum : data.getData()) {
			int[] states = datum.getStates();
			P.set(states[0], states[1], datum.getWeight()/data.getTotalWeight());
		}


		return P;
	}
	
	
	  private Matrix Normalize_Col(Matrix inputM){
		  	Matrix outputM = new Basic2DMatrix();
	    	outputM = inputM.copy();
	    	double[] sum = new double[outputM.columns()];
	    	for(int j=0;j<outputM.columns();j++){
	    		sum[j] = outputM.getColumn(j).sum();
	    		Vector col = outputM.getColumn(j).divide(sum[j]);
	    		outputM.setColumn(j, col);
	    	}
	    	
	    	return outputM;
	    	
	    }
	 
	    private Matrix Normalize_Row(Matrix inputM){
	    	Matrix outputM = new Basic2DMatrix();
	    	outputM = inputM;
	    	double[] sum = new double[outputM.rows()];
	    	for(int j=0;j<outputM.rows();j++){
	    		sum[j] = outputM.getRow(j).sum();
	    		Vector row = outputM.getRow(j).divide(sum[j]);
	    		outputM.setRow(j, row);
	    	}
	    	
	    	return outputM;
	    	
	    }
	    
	
	

	/*
	public void createdata(String[] args) throws IOException, Exception
	{
		
		//True model
		_model = new LTM();
		Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		parser.parse(_model);
		_Variables.addAll(_model.getManifestVars());
		
		doSample(Integer.parseInt(args[1]),Integer.parseInt(args[1]));
	
	}
*/

   
	
/*	private BayesNet postProcessingModel(BayesNet model)
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
	*/
	private BayesNet BuildHierarchy(BayesNet OldModel, BayesNet tree) {
		
		BayesNet CurrentModel = new BayesNet();
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

			
			ArrayList<Double> CptArray = new ArrayList<Double>();
			double[] Cpt = bnd.getCpt().getCells();
			/*for(int i=0;i<Cpt.length;i++){
				CptArray.add(Cpt[i]);
			}*/
			//CurrentModel.getNodeByName(nd.getName()).replaceVar(bnd.getVariable());
			CurrentModel.getNodeByName(nd.getName()).getCpt().setCells(pair,Cpt);
		}
		
		}

		return CurrentModel;
	}	
	
	
	private DataSet HardAssignment(BayesNet CurrentModel, BayesNet latentTree) {
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

		if (set.size() != varName.length) {
			for (Variable var : varName) {
				if (!set.contains(var)) {
					_InternalNodeNeedRemove.add(var);
				}
			}
			da = da.project(set,false);
		}

		return da;
	}
	
	
	
    private void evaluate(BayesNet _modelEst2){
    	
    	double Loglikelihood= ScoreCalculator.computeLoglikelihood((BayesNet)_modelEst2, _test);
    	double perLL = Loglikelihood/_test.getTotalWeight();
    	System.out.println("PerP(D|theta)_ "+_modelEst2.getName()+ "=  "+perLL);
    }
	
//	private void evaluateTrue(){
//		double Loglikelihood= ScoreCalculator.computeLoglikelihood((BayesNet)_model, _test);
//    	double perLL = Loglikelihood/_test.getTotalWeight();
//    	System.out.println("PerP(D|theta)_true"+ perLL);
//	}
	
	
	   
  
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
	
	/**
	 * Initialize before building each layer
	 * @param data
	 */
	
	protected void initialize(DataSet data) {
		System.out.println("=== Initialization ===");

		// initialize data structures for P(Y|d)
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
	
	
  
/*
	private LTM smoothingLTM(LTM model)
	{
		for(Variable var : model.getVariables())
		{
			if(!model.getNode(var).isRoot()){
				Matrix cptMat=FunToMat(model.getNode(var));		
				if(cptMat.max()>0.999||cptMat.min()<0.001){
					for(int i=0; i<cptMat.rows(); i++)
					{
						for(int j=0;j<cptMat.columns();j++){
							cptMat.set(i, j, (cptMat.get(i, j)+Math.random()*0.01));
						}	
					}
					cptMat = Normalize_Col(cptMat);
					setVdis(var,cptMat,model);
				}
			}	
 		}
		return model;
	}
	
	*/
	
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

}

