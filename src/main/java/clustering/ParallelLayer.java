package clustering;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
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
import org.latlab.learner.ParallelEmLearner;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.ScoreCalculator;
import org.latlab.util.Variable;
import org.latlab.util.DataSet.DataCase;
import org.mymedialite.util.Random;

import clustering.StepwiseEMHLTA.ConstHyperParameterSet;

/**
 * Written for learning the first layer in parallel. Should work for all layers except the top layer.
 * With slight modification can also work for the top layer
 * @author fkhawar
 *
 */

public class ParallelLayer {
	private static ForkJoinPool threadPool = null;
	
	/**
	 * This Array will contain the variables in randomised order and they are populated from the original _VariablesSet 
	 */
	protected static ArrayList<Variable> _VariableArray;
	
	/**
	 * Data of the current layer
	 */
	protected  static DataSet _data; 
	
	/**
	 * This list will store the random order of the variables. each index if this array stores the location of the 
	 * variable after randomization
	 */
	protected ArrayList<Integer> _randomVariableIndex; // CHANGE IT TO get index from Orign data to random 
	/**
	 * Original data : we will need it fort hard assignment
	 */
	protected static  DataSet _OrigDenseData;
	
	protected static ArrayList<Variable> _OriVariables;
	
	protected static int _OriVariablesSize;
	
	public LTM tmpTree;
	public Map<Variable, LTM> hierarchies;
	public DataSet tmpData;
	public Map<Variable, Map<DataCase, Function>> localLatentPosts;
	public Map<Variable, Map<DataCase, Function>> globalLatentPosts;
	public Map<String, ArrayList<Variable>> bestpairs;
	
	/*public Set<LatentPostsPackage> newLatentPostsPackageSet() {
		return new HashSet<LatentPostsPackage>();
	}*/
	
	public ParallelLayer() {
		
	}
	
	public void parallelLayer(DataSet OrigDenseData, DataSet data,
			StepwiseEMHLTA.ConstHyperParameterSet hyperParam,
			ArrayList<Variable> OriVariables){
		
		ParallelLayer._data = data;
		
		ParallelLayer._OrigDenseData = OrigDenseData;
		ParallelLayer._OriVariables = OriVariables;
		ParallelLayer._OriVariablesSize = ParallelLayer._OriVariables.size();
		
		// create _VariableArray from _VariablesSet
		fillVariableArrayRandomly(data.getVariables());
		
		// set all the data as context
		parallelLayerComputation.Context context =
			new parallelLayerComputation.Context(hyperParam._UDthreshold, hyperParam, 
					hyperParam._maxIsland, ParallelLayer._OriVariablesSize);
		
		
		parallelLayerComputation computation =
			new parallelLayerComputation(context, 0, data.getVariables().length);
		
		getForkJoinPool().invoke(computation);
		
		hierarchies = computation.getHierarchies();
		tmpTree= computation.getTree();
		// tie all the trees together to make one tree, every computation also aves its root, each merge we tie them using identity function
		
		tmpData = computation.getData();
		localLatentPosts = computation.getLocalLatentPosts();
		bestpairs = computation.getBestPairs();
		globalLatentPosts = parallelLayerComputation.localPosts2GlobalPostsAll(computation.getLatentPostsPackageSet());
	}
	
	public LTM getTmpTree() {
		return tmpTree;
	}
	
	public DataSet getTmpData() {
		return tmpData;
	}
	
	public Map<Variable, LTM> getHierarchies() {
		return hierarchies;
	}
	
	public Map<Variable, Map<DataCase, Function>> getLocalLatentPosts() {
		return localLatentPosts;
	}
	
	public Map<Variable, Map<DataCase, Function>> getGloballLatentPosts() {
		return globalLatentPosts;
	}
	
	public Map<String, ArrayList<Variable>> getBestPairs() {
		return bestpairs;
	}
	
	/**
	 * Randomize the order of the datacases. create an list with size _totalDatacases and in which
	 * each entry creates a randomized index to original datacase
	 */
	private void randomiseVariableIndex(int NumOfVariables){

		// First build the index if it does not exist or is of less size
		if (_randomVariableIndex == null || _randomVariableIndex.size() != NumOfVariables) {
			_randomVariableIndex = new ArrayList<Integer>(NumOfVariables);
		      for (int index = 0; index < NumOfVariables; index++)
		    	  _randomVariableIndex.add(index, index);
		    }
		// Then randomize it
		Random rand = Random.getInstance();
		
		Collections.shuffle(_randomVariableIndex, rand);
		
		// Before returning the latent tree merge all the trees
	}
	
	/**
	 * 
	 * @return
	 */
	
	private void fillVariableArrayRandomly( Variable[] WorkingDataVariables) {
		
		randomiseVariableIndex(WorkingDataVariables.length);
		_VariableArray= new ArrayList<Variable>(WorkingDataVariables.length);
		
		for(int i =0 ; i < WorkingDataVariables.length ; i++) {
			_VariableArray.add(i, WorkingDataVariables[_randomVariableIndex.get(i)]);
		}
		//System.out.println("WorkingDataVariables.length:" + WorkingDataVariables.length);
		System.out.println("Total number of manifest Variables in Layer1:" +_VariableArray.size());
	}
	
	protected static ForkJoinPool getForkJoinPool() {
		if (threadPool == null)
			threadPool = new ForkJoinPool();

		return threadPool;
	}

	
	@SuppressWarnings("serial")
	public static class parallelLayerComputation extends RecursiveAction {

		public static class Context {
			
			/**
			 * Threshold at after which we would split into 2, i.e maximum variable size for one thread
			 * to iterate over
			 */
			public final int splitThreshold;
			public double _UDthreshold;
			public ConstHyperParameterSet _hyperParam;
			public int _maxIsland;
			//public int Level;
			public int _OriVariablesSize;

			public Context(
					double _UDthreshold, ConstHyperParameterSet hyperParam, int _maxIsland, int OriVariablesSize
					) {
				
				
				this._UDthreshold =_UDthreshold;
				this._hyperParam = hyperParam;
				this._maxIsland= _maxIsland;
				//this.Level = Level;
				this._OriVariablesSize = OriVariablesSize;
				double NumSplits = (double) Runtime.getRuntime().availableProcessors()>8?8:Runtime.getRuntime().availableProcessors(); // making the max split to be 8
				//NumSplits = 1;
				splitThreshold =(int) Math.ceil(_VariableArray.size() / NumSplits);
				System.out.println("NumSplits: " + NumSplits + " splitThreshold: " + splitThreshold + " _VariableArray.size(): " + _VariableArray.size());
			}
		}
		
		public class LatentPostsPackage {
			public Map<Variable, Map<DataCase, Function>> _latentPosts;
			public DataSet _data;
			
			public LatentPostsPackage() {
				_latentPosts = new HashMap<Variable, Map<DataCase, Function>>();
			}
		}
		
		public Set<LatentPostsPackage> getLatentPostsPackageSet() {
			return latentPostsPackages;
		}
		
		private final Context context;
		private final int start;
		private final int length;
		
		public LTM tree;
		public Map<Variable, LTM> hierarchies;
		public Map<Variable, Map<DataCase, Function>> localLatentPosts;
		//public Map<Variable, Map<DataCase, Function>> globalLatentPosts;
		public LatentPostsPackage latentPostsPackage = new LatentPostsPackage();
		public Set<LatentPostsPackage> latentPostsPackages = new HashSet<LatentPostsPackage>();
		public Map<String, ArrayList<Variable>> bestpairs;
		public DataSet hardAssign;
		private BeliefNode root;
		
		
		
		public parallelLayerComputation(Context context, int start, int length) {
			this.context = context;
			this.start = start;
			this.length = length;
		}
		
		public LTM getTree() {
			return tree;
		}
		
		public Map<Variable, LTM> getHierarchies() {
			return hierarchies;
		}
		
		public Map<Variable, Map<DataCase, Function>> getLocalLatentPosts() {
			return localLatentPosts;
		}
		
		public Set<LatentPostsPackage> getLatentPostsPackages() {
			return latentPostsPackages;
		}
		
		/*public Map<Variable, Map<DataCase, Function>> getGlobalLatentPosts() {
			return globalLatentPosts;
		}*/
		
		public Map<String, ArrayList<Variable>> getBestPairs() {
			return bestpairs;
		}
		
		public DataSet getData() {
			return hardAssign;
		}
		
		@Override
		protected void compute() {
			if (length <= context.splitThreshold || 500 >= context._OriVariablesSize) {
				try {
					computeDirectly();
				} catch (FileNotFoundException | UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return;
			}

			int split = length / 2;
			parallelLayerComputation c1 = new parallelLayerComputation(context, start, split);
			parallelLayerComputation c2 =
					new parallelLayerComputation(context, start + split, length - split);
			invokeAll(c1, c2);			
		    
			// first copy c1 to the current tree
			tree = c1.tree.clone();
			// then merge c2 to the current tree to make it a forest
			CopyNodes(tree,c2.tree);
			CopyEdgesCPT(tree,c2.tree);
			hierarchies = MergeHierarchies(c1.hierarchies, c2.hierarchies);
			localLatentPosts = MergeLocalLatentPosts(c1.localLatentPosts, c2.localLatentPosts);
			latentPostsPackages.addAll(c1.latentPostsPackages);
			latentPostsPackages.addAll(c2.latentPostsPackages);

			bestpairs = MergeBestPairs(c1.bestpairs, c2.bestpairs);
			
			// create and edge between two roots 
			BeliefNode newNode =
					tree.getNode(c1.root.getVariable());
			BeliefNode newParent =
					tree.getNode(c2.root.getVariable());
			tree.addEdge(newNode, newParent); // c1 child, c2 parent. Add the edge
			ArrayList<Variable> roots = new ArrayList<Variable>();
			roots.add(newNode.getVariable());roots.add(newParent.getVariable());
			//Function tmp = Function.createFunction(roots);
		//	newNode.setCpt(tmp); // create their CPT
			
			
			Function cpt = ((BeliefNode) newParent).getCpt();
			cpt.randomlyDistribute(((BeliefNode) newParent).getVariable());
			((BeliefNode) newParent).setCpt(cpt);
			
			Function cpt1 = ((BeliefNode) newNode).getCpt();
			cpt1.randomlyDistribute(((BeliefNode) newNode).getVariable());
			((BeliefNode) newNode).setCpt(cpt1);
			
			
			root = tree.getRoot(); // update the current root
			
			/*//if(context.Level==2) {
		    // merge the dataset over the same users(datacase) from c1 and c2 i.e. concatenate the dataset over variables from c1 and c2			
			Variable[] c1DataVars= c1.hardAssign.getVariables();
			Variable[] c2DataVars= c2.hardAssign.getVariables();
			Variable[] vars = new Variable[c1DataVars.length+c2DataVars.length];
			
			// copy the variables
			for (int i =0 ; i< c1DataVars.length;i++) {
				vars[i]=new Variable(c1DataVars[i].getName(),c1DataVars[i].getStates());
			}
			for (int j =0; j< c2DataVars.length;j++) {
				vars[j+c1DataVars.length]=new Variable(c2DataVars[j].getName(),c2DataVars[j].getStates());
			}
			
			// the array that will store the new states
			int[][] newData = new int[_OrigDenseData.getNumberOfEntries()][vars.length];
			
			ArrayList<DataCase> c1Data = c1.hardAssign.getData();
			ArrayList<DataCase> c2Data = c2.hardAssign.getData();
			
			// populate the states for all the datacases
			for (int d =0 ; d <_OrigDenseData.getNumberOfEntries();d++) {
				
				int[] dc1 =  c1Data.get(d).getStates();
				for (int i =0 ; i< c1DataVars.length;i++) {
					newData[d][i]=dc1[i];
				}
				
				int[] dc2 =  c2Data.get(d).getStates();
				for (int j =0 ; j< c2DataVars.length;j++) {
					newData[d][j+c1DataVars.length]=dc2[j];
				}
				
			}
			
			
			// create the new dataset
			hardAssign = new DataSet(vars);
			for (int j = 0; j < _OrigDenseData.getNumberOfEntries(); j++) {
				hardAssign.ForceAddDataCase(newData[j], c1Data.get(j).getWeight(),j); // since c1 was also hardassigned from _OrigDenseData it will have the same num of datacases and the same weight for each datacase as _OrigDenseData
			}
			//}*/
			
		}
		
		private void computeDirectly() throws FileNotFoundException, UnsupportedEncodingException  {
			FastLTA_flat();
		}
		
		/**
		 * Build One layer
		 * following the StepwiseEMGLTA.FastLTA_flat.
		 * This function is the parallel version of StepwiseEMGLTA.FastLTA_flat.
		 * @return
		 * @throws UnsupportedEncodingException 
		 * @throws FileNotFoundException 
		 */
		public void FastLTA_flat() throws FileNotFoundException, UnsupportedEncodingException {
			
			// Storing similarity for the variables on this core
			ArrayList<double[]> mis=new ArrayList<double[]>();
		
			// the dataset for this core
			ArrayList<Variable> subArrayVariables = new ArrayList<Variable>();
			for (int k =start ; k < start+length ; k++) {
				subArrayVariables.add(_VariableArray.get(k));
			}
			
			/*System.out.println("-1 data.getData().size():" + _data.getData().size());
			for (DataCase d : _data.getData()) {
				System.out.println("-1 data set in data: " + d);
			}*/
			_data.assignGlobalID();
			DataSet splitData = _data.project(subArrayVariables);
			for (DataCase d : _data.getData()) {
				splitData._globalID2GlobalDataCase.put(d.getGlobalID(), d);
				//System.out.println("_globalID2GlobalDataCase put data: " + d + " id: " + d.getGlobalID());
			}
			splitData.updateID2DataMap();

			// the variable array for this core
			ArrayList<Variable> Variables = new ArrayList<Variable>();
			
			// The variable set for this core
			HashSet<Variable> VariablesSet = new HashSet<Variable>();
			
			// add all manifest variable to variable set _VariableSet.
			for (Variable var : splitData.getVariables()) {
				Variables.add(var);   // getting the varibles from data here instead of subArrayVariables becasue want to ensure that the order of the variables is the same as that of the dataset
				VariablesSet.add(var);
			}
			
			Map<String, Integer> varId = new HashMap<String, Integer>();
			for(int i =0;i<Variables.size();i++){
				varId.put(Variables.get(i).getName(), i);
			}
			
			// Stoing intermediate islands
			hierarchies = new HashMap<Variable, LTM>();
			
			// Storing best pairs of each island
			//Map<String, ArrayList<Variable>> bestpairs = new HashMap<String, ArrayList<Variable>>();
			bestpairs = new HashMap<String, ArrayList<Variable>>();
			
			StepwiseEMHLTA.IslandFinding(VariablesSet, varId, splitData, mis,
					context._hyperParam, bestpairs, hierarchies, Variables, start);

			// link the islands.
			localLatentPosts = new HashMap<Variable, Map<DataCase, Function>>();
			tree = StepwiseEMHLTA.BuildLatentTree(splitData, hierarchies, bestpairs, localLatentPosts, context._hyperParam);
			
			latentPostsPackages.clear();
			latentPostsPackage._latentPosts = localLatentPosts;
			latentPostsPackage._data = splitData;
			latentPostsPackages.add(latentPostsPackage);
			System.out.println("======================= tree has been built  =================================");
			
			root = tree.getRoot();
			
			//if (context.Level==2) {
			// Do hard assignment of this local model
			/*hardAssign = HardAssignment(tree, tree,_data,Variables);// MAKE SURE that the order of the datacases remains same after projection****************
			System.out.println("======================= hard assignment has been done  =================================");
			*/
			//}
			
		}
		
			
		/**
		 * map[i] contains where data variable j would be
		 * @param data
		 * @param Variables
		 * @return
		 */
		private static int[] ProjectMap(DataSet data,Variable[] varArray) {
			// array representation
			Variable[] dataVariables = data.getVariables();

			// maps argument variables to that in this data set
			int dimension = varArray.length;
			int[] map = new int[dimension];
	
			////////////////////////////////////////////////////////////////
			for(int i = 0; i < dimension; i++)
			{
				for(int j = 0; j < dataVariables.length; j++)
				{
					if(dataVariables[j].getName().compareTo(varArray[i].getName()) == 0)
					{
						map[i] = j;
					}
				}
			}
			return map;
			
	
		}
		
		private static int[] ProjectStates(DataCase dataCase, int[] map, int dimension) {
			// projection
			
				int[] projectedStates = new int[dimension];

				int[] states = dataCase.getStates();
				for (int i = 0; i < dimension; i++) {
					projectedStates[i] = states[map[i]];
				}

			

			return projectedStates;
		}
		
		public static DataSet HardAssignment(LTM CurrentModel, LTM latentTree, DataSet _OrigDenseData, ArrayList<Variable> Variables) {
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

			
			Variable[] varArray = Variables.toArray(new Variable[Variables.size()]);// need to project on the manifest variables. Make sure varArray are the actual manifest vars
			int[] map = ProjectMap(_OrigDenseData, varArray);
			
			// update for every data case
			for (int j = 0; j < data.size(); j++) {
				DataCase dataCase = data.get(j);

				
				int[] states = ProjectStates( dataCase, map,Variables.size());
				// Need to project the states for the subset of variables in this subtree

				// set evidence and propagate
				ctp.setEvidence(varArray, states);
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
				da.ForceAddDataCase(newData[j], data.get(j).getWeight(),j);
			}

			/*ArrayList<Variable> set = new ArrayList<Variable>();
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
			}*/

			return da;
		}
		
		/*private boolean isDone(HashSet<Variable> VariableSet) {
			return VariableSet.size() < 1;
		}*/
		
		/**
		 * merge hierarchies: hierarchies1 + hierarchies2 = hierarchies1+2
		 * @param input: hierarchies1, hierarchies2
		 * @param output: hierarchies1+2
		 */
		private Map<Variable, LTM> MergeHierarchies(Map<Variable, LTM> hierarchies1, Map<Variable, LTM> hierarchies2) {
			Map<Variable, LTM> hierarchies = new HashMap<Variable, LTM>();
			hierarchies.putAll(hierarchies1);
			hierarchies.putAll(hierarchies2);
			return hierarchies;
		}
		
		/**
		 * merge localLatentPosts: localLatentPosts1 + localLatentPosts2 = localLatentPosts1+2
		 * @param input: localLatentPosts1, localLatentPosts2
		 * @param output: localLatentPosts1+2
		 */
		private Map<Variable, Map<DataCase, Function>> MergeLocalLatentPosts(
				Map<Variable, Map<DataCase, Function>> lp1, 
				Map<Variable, Map<DataCase, Function>> lp2) {
			Map<Variable, Map<DataCase, Function>> lp = new HashMap<Variable, Map<DataCase, Function>>();

			lp.putAll(lp1);
			lp.putAll(lp2);
			return lp;
		}
		
		/**
		 * merge bestPairs: bestPairs1 + bestPairs2 = bestPairs1+2
		 * @param input: bestPairs1, bestPairs2
		 * @param output: bestPairs1+2
		 */
		public Map<String, ArrayList<Variable>> MergeBestPairs(
				Map<String, ArrayList<Variable>> bp1,
				Map<String, ArrayList<Variable>> bp2) {
			Map<String, ArrayList<Variable>> bp = new HashMap<String, ArrayList<Variable>>();
			bp.putAll(bp1);
			bp.putAll(bp2);
			return bp;
		}
		
		/**
		 * Copy nodes from tmpTree to LatentTree
		 * @param latentTree
		 * @param tempTree
		 */
		private void CopyNodes(LTM latentTree, LTM tempTree) {
			for (AbstractNode node : tempTree.getNodes()) {
				latentTree.addNode(((BeliefNode) node).getVariable());
			}
		}
		
		/**
		 * Copy Edges and CPT from tmpTree to LatentTree
		 * @param latentTree
		 * @param tempTree
		 */
		private void CopyEdgesCPT(LTM latentTree, LTM tempTree) {
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
		
		private static Map<Variable, Map<DataCase, Function>> localPosts2GlobalPosts(
				Map<Variable, Map<DataCase, Function>> local, 
				DataSet localDataSet) {
			Map<Variable, Map<DataCase, Function>> global = new HashMap<Variable, Map<DataCase, Function>>();
			for (Variable v : local.keySet()) {
				Map<DataCase, Function> data2func = new HashMap<DataCase, Function>();
				for (DataCase d : local.get(v).keySet()) {
					int globalID = localDataSet._dataCase2GlobalID.get(d);
					DataCase globalDataCase = localDataSet._globalID2GlobalDataCase.get(globalID);
					data2func.put(globalDataCase, local.get(v).get(d));
				}
				global.put(v, data2func);
			}
			return global;
		}
		
		private static Map<Variable, Map<DataCase, Function>> localPosts2GlobalPostsAll(
				Set<LatentPostsPackage> lpps) {
			Map<Variable, Map<DataCase, Function>> globalLatentPosts = new HashMap<Variable, Map<DataCase, Function>>();
			for (LatentPostsPackage lpp : lpps) {
				globalLatentPosts.putAll(localPosts2GlobalPosts(lpp._latentPosts, lpp._data));
			}
			
			return globalLatentPosts;
		}
				
	}
	
}





