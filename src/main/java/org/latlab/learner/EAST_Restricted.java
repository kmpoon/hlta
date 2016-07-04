package org.latlab.learner;

import org.latlab.graph.DirectedNode;
import org.latlab.graph.AbstractNode; // import hlcm.HLCM;
import org.latlab.learner.EmLearner;
import org.latlab.learner.LocalEmLearner;
import org.latlab.model.LTM;
import org.latlab.model.BeliefNode;
import org.latlab.util.*;
import org.latlab.reasoner.*;
import org.latlab.util.DataSet.DataCase;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * 
 * This is a simplified version of EAST with some restrictions.
 * 
 * It is used in Bridged-Islands algorithms.
 * 
 */

public class EAST_Restricted {

//	public double totalEvaluationTime = 0.0;

	private boolean _useIR = true;

	public void setUseIR(boolean useIR) {
		_useIR = useIR;
	}

	/**
	 * Total candidate models evaluated;
	 */
	public int candidateModels = 0;

//	/**
//	 * totalFullEMTime add up all the times used for fullEM best candidate
//	 * models;
//	 */
//	public double totalFullEMTime = 0.0;

	private LocalEmLearner _localEmLearner = new LocalEmLearner();

	/*
	 * "ChickeringHeckerman" or "MultipleRestarts"
	 */
	private String _localMaximaAvoidanceMechanism;

	// When _localMaximaAvoidanceMechanism = "MultipleRestarts",
	// _localEMSetting[0,1,2] are respectively (0) NumberOfRestarts, (1)
	// NumberOfPreSteps and (2)NumberOfContinuedSteps; When
	// _localMaximaAvoidanceMechanism = "ChickeringHeckerman",
	// _localEMSetting[0,1,2] are respectively (0) NumberOfRestarts, (1)
	// nonsense and (2) NumberOfContinuedSteps;
	private int[] _localEMSettings = new int[3];

	/**
	 * Specify the settings for localEM.
	 * 
	 * @param localMaximaAvoidanceMechanism
	 * @param localEMSettings
	 */
	public void setLEMSettings(String localMaximaAvoidanceMechanism,
			int[] localEMSettings,double threshold) {
		_localMaximaAvoidanceMechanism = localMaximaAvoidanceMechanism;
		_localEMSettings = localEMSettings;
		_localEmLearner.setThreshold(threshold);
	}

	private void setLocalEMLearner(boolean reuse) {
		//_localEmLearner.setThreshold(Double.MIN_VALUE);
		_localEmLearner.setLocalMaximaEscapeMethod(_localMaximaAvoidanceMechanism);
		_localEmLearner.setReuseFlag(reuse);

		_localEmLearner.setNumberOfRestarts(_localEMSettings[0]);
		_localEmLearner.setNumberOfContinuedSteps(_localEMSettings[2]);

		if (_localMaximaAvoidanceMechanism.equals("MultipleRestarts")) {
			_localEmLearner.setNumberOfPreSteps(_localEMSettings[1]);
		} else if (_localMaximaAvoidanceMechanism.equals("ChickeringHeckerman")) {
		} else {
			System.out.println(" Unknown method for local maxima avoidance ");
			System.exit(1);
		}
	}

//	private EmLearner _emLearner = new EmLearner();
	private ParallelEmLearner _emLearner = new ParallelEmLearner();

	// Settings of fullEM on the bestCandModel
	private int _nRestarts, _nMaxSteps;

	/**
	 * The EM threshold.
	 */
	private double _EMthreshold;
	
	private double _BICthreshold = 1;

	/**
	 * Specify the settings for global EM.
	 * 
	 * @param nRestarts
	 * @param nMaxSteps
	 * @param threshold
	 */
	public void setEMSettings(int nRestarts, int nMaxSteps, double threshold) {
		_nRestarts = nRestarts;
		_nMaxSteps = nMaxSteps;
		_EMthreshold = threshold;
	}

	private void setEMLearner() {
		_emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		_emLearner.setReuseFlag(true);
		_emLearner.setNumberOfRestarts(_nRestarts);
		_emLearner.setMaxNumberOfSteps(_nMaxSteps);
		_emLearner.setThreshold(_EMthreshold);
	}
	
	public void setBICThreshold(double threshold)
	{
		_BICthreshold = threshold;
	}

	/**
	 * Restrict the length of sortedModelList.
	 */
	private int _length = 10;

	private Map<DataCase, CliqueTreePropagation> _ctps = new HashMap<DataCase, CliqueTreePropagation>();

	private int _algoStep = 0;

	private ArrayList<Variable> _ClosestVariablePair = null;

	public void setClosestVariablePair(ArrayList<Variable> ClosestVariablePair) {
		_ClosestVariablePair = ClosestVariablePair;
	}
	
//	private ArrayList<Variable> _initialPair = null;
//	public void setInitialPair(ArrayList<Variable> initialPair)
//	{
//		_initialPair = initialPair;
//	}

	/**
	 * Search starting from an LC model.
	 * 
	 * @param dataSet
	 *            The dataset.
	 * @return An HLC model of highest BIC score.
	 */
	public LTM search(DataSet dataSet) {
		Variable[] maniVars = dataSet.getVariables();
		LTM lCM = LTM.createLCM(maniVars, 2);
		LTM model = search(lCM, dataSet);
		return model;
	}

	/**
	 * This is a simplified version of EAST. To learn an LTM, only expand operator is considered
	 * here. 
	 * 
	 * @param initModel
	 * @param data
	 * @param preAdjust
	 *            Whether by all means adjust the initModel at the begining
	 * @return the best model
	 */
	public LTM search(LTM initModel, DataSet data) 
	{
		setEMLearner();
		LTM bestModel = (LTM) _emLearner.em(initModel, data);
		_algoStep++;

		// Only expand operator is needed.
		bestModel = expand(bestModel, data);

		return bestModel;
	}


	/**
	 * Continue expanding the model until expansion could not bring any
	 * improvement in model quality. This method will return a model which is
	 * either a better model or at least the input model.
	 * 
	 * @param model
	 * @param data
	 * @return A mode which cannot be expanded.
	 */
	private LTM expand(LTM model, DataSet data) {
		LTM bestModel = model;
		LTM nextBestModel = null;

		while (true) 
		{
			// Explaination of info:
			// info[0]: a Boolean object which is true iff the returned model is
			// generated from NI.
			// info[1]: When info[0] is true, info[1] is the original variable.
			// info[2]: When info[0] is true, info[2] is the new variable.
			Object[] info = { null, null, null };
			nextBestModel = oneStepExpand(bestModel, data, info);
		
			if (nextBestModel.getBICScore(data) - bestModel.getBICScore(data) > _BICthreshold) 
			{
				bestModel = nextBestModel;

				if ((Boolean) info[0]) 
				{
					_algoStep++;
					bestModel = restrictedAdjust(bestModel, (Variable) info[1],(Variable) info[2], data);
					//break;
				}
				else 
				{
					_algoStep++;
				}
			} else 
			{
				break;
			}
		}
		return bestModel;
	}

	/**
	 * Continue adjusting the model restrictedly until restricted adjustment
	 * could not bring any improvement in model quality. This method will return
	 * a model which is either a better model or at least the input model.
	 * 
	 * @param model
	 * @param original
	 * @param newVar
	 * @param data
	 * @return
	 */
	private LTM restrictedAdjust(LTM model, Variable originalVar, Variable newVar, DataSet data) 
	{
		LTM bestModel = model;
		LTM nextBestModel = null;

		// TODO Thinking the condition some more
		while (bestModel.getLatVars().size() >1 && bestModel.getNode(originalVar).getDegree() >= 2) 
		{
//			System.out.println("\nRestriAdjust Model");
			nextBestModel = oneStepRestriAdjust(bestModel, originalVar, newVar,data);
			if (nextBestModel.getBICScore(data) - bestModel.getBICScore(data) > _BICthreshold) 
			{
//				System.out.println("Succeed\n");
				bestModel = nextBestModel;

//				printModelInfo(bestModel, data, "resAdjust");
				_algoStep++;
			} else {
//				System.out.println("Fail\n");
				break;
			}
		}
		return bestModel;
	}

	/*
	 * We evaluate all possibilities by restricted NR. The best one is selected
	 * and fully EMed. <b> Note </b> that the returned model might be not as
	 * good as current model.
	 */
	private LTM oneStepRestriAdjust(LTM model, Variable oldVar,
		Variable newVar, DataSet data) {
		LTM bestModel = model;
		computeCtps(bestModel, data);

		LTM nextBestModel = bestModelRestriNR(bestModel, oldVar, newVar, data);
		
		_ctps.clear();
		System.gc();

		nextBestModel = (LTM) _emLearner.em(nextBestModel, data);

		return nextBestModel;
	}




	/**
	 * Re-consider the cardinality of latent nodes according to the new whole model.
	 * 
	 * @param model
	 * @param data
	 * @return
	 */
	public LTM refineStructureAndState(LTM model, DataSet data, Map<Variable, Variable> map) {
		
	    //re-consider the cardinality.
		model = reconsiderCardinality(model, data, map);
		
		//re-consider the structure.
		model = reconsiderStructure(model, data, map);

		return model;
	}

	/**
	 * Given the current model curModel, we can generate a set of candidate
	 * models through SI and NI which expands the model space. We then returns
	 * the best one <b>in terms of unit increase</b>.
	 * 
	 * Note that when comparing among candidate models, they are not globally
	 * EMed. But for the bestCandModel which is determined to be returned, we
	 * finally do full EM and then return it.
	 * 
	 * @param curModel
	 * @param data
	 * @param info
	 *            info[0]: a Boolean object which is true iff the returned model
	 *            is generated from NI; info[1]: When info[0] is true, info[1]
	 *            is the original variable; info[2]: When info[0] is true,
	 *            info[2] is the new variable.
	 * 
	 * @return The bestModel through expansion
	 */
	private LTM oneStepExpand(LTM model, DataSet data, Object[] info) {
		LTM bestModel = model;
		computeCtps(bestModel, data);

		LTM nextBestModelSI = bestModelSI(bestModel, data);

		// if current model already has more than one latent nodes, then do not
		// introduce new latent node.

		LTM nextBestModelNI = null;
		
		//Restrict the number of latent nodes.If there are more than 2 latent nodes in this sub-model,
		//there is no need to consider NI.
		if (model.getLatVars().size() < 2) {
			nextBestModelNI = bestModelNI(bestModel, data, info);
		}

		LTM nextBestModel = null;

		if (nextBestModelNI == null && nextBestModelSI == null) {// Sometimes,we have both null.
			nextBestModel = model;
			info[0] = new Boolean(false);
		} else if (nextBestModelNI == null) {
			nextBestModel = nextBestModelSI;
			info[0] = new Boolean(false);
		} else if (nextBestModelSI == null) {
			nextBestModel = nextBestModelNI;
			info[0] = new Boolean(true);
		} else {
			double BICNI = nextBestModelNI.getBICScore(data);
			double BICSI = nextBestModelSI.getBICScore(data);
			double BIC = bestModel.getBICScore(data);
			double UINI = computeUI(bestModel, nextBestModelNI, data);
			double UISI = computeUI(bestModel, nextBestModelSI, data);
			if (_useIR) {
				if ((BICNI <= BIC || BICSI <= BIC) && BICNI > BICSI) {
					nextBestModel = nextBestModelNI;
					info[0] = new Boolean(true);
				} else if (BICNI > BIC && BICSI > BIC && UINI > UISI) {
					nextBestModel = nextBestModelNI;
					info[0] = new Boolean(true);
				} else {
					nextBestModel = nextBestModelSI;
					info[0] = new Boolean(false);
				}
			} else {
				if (BICNI > BICSI) {
					nextBestModel = nextBestModelNI;
					info[0] = new Boolean(true);
				} else {
					nextBestModel = nextBestModelSI;
					info[0] = new Boolean(false);
				}
			}
		}
		
		return nextBestModel;
	}

	/**
	 * Generate candidate models and evaluate them. We will return (1) null if
	 * there is no candidate model (2) the best model according to our
	 * evaluation.
	 * 
	 * @param model
	 * @param data
	 * @param info
	 * @see <code>oneStepExpand</code>
	 * @return
	 */
	public LTM bestModelNI(LTM model, DataSet data, Object[] info) {
		ArrayList<LTM> sortedModelList = new ArrayList<LTM>();
		setLocalEMLearner(false);
		double optimal = -Double.MAX_VALUE;

//		int currentDim = model.computeDimension();
//		double currentBIC = model.getBICScore(data);

		for (Variable originalVar : model.getLatVars()) 
		{
			// Preparation
			ArrayList<Variable> neighbors = new ArrayList<Variable>();
			for (AbstractNode node : model.getNode(originalVar).getNeighbors()) {
				neighbors.add(((BeliefNode) node).getVariable());
			}
			ArrayList<Variable> children = new ArrayList<Variable>();
			for (DirectedNode node : model.getNode(originalVar).getChildren()) {
				children.add(((BeliefNode) node).getVariable());
			}
			int nOfChildren = children.size();
//			Variable parent = null;
//			if (!model.getNode(originalVar).isRoot())
//				parent = ((BeliefNode) model.getNode(originalVar).getParent())
//						.getVariable();

			// Case 0: The introduction of a same-cardinality node when there
			// are only three neighbors has no improvement in the model quality.
			if (neighbors.size() < 4)
				continue;

			
			//Variable copyVar = new Variable(originalVar.getCardinality());
				
			// Case 1: For two children
			for (int j = 0; j < nOfChildren; j++)
			{		   
					Variable vj = children.get(j);

					if (_ClosestVariablePair==null || _ClosestVariablePair.get(1).equals(vj) || !_ClosestVariablePair.get(0).equals(vj))
					{
						continue;
					}
					
					Variable vi = _ClosestVariablePair.get(1);
					 					
				double local_optimal = -Double.MAX_VALUE;
				for(int card = 2; card < originalVar.getCardinality()+1; card++ )
				{
						
					Variable copyVar = new Variable(card);

					// Generate candidate model and randomly set the "new"
					// parameters!
					LTM model_ = model.manipulation_NodeIntroduction(originalVar, vi, vj, copyVar);

					// Construct repository for localEM
					Map<DataCase, Set<MessagesForLocalEM>> repository = new HashMap<DataCase, Set<MessagesForLocalEM>>();	
					Variable[] families = new Variable[] { vi, vj, copyVar };
					Variable[] variables = new Variable[] { copyVar };
					CliqueTreePropagation template_ = new CliqueTreePropagation(model_, families, variables);
					template_.getCliqueTree().prune();

					Variable[] variables_enlarge = new Variable[] { copyVar,originalVar };
					CliqueTreePropagation ctp_enlarge = new CliqueTreePropagation(model_, families, variables_enlarge);
					CliqueTree ct_enlarge = ctp_enlarge.getCliqueTree();
					ct_enlarge.prune();
					CliqueNode source = ct_enlarge.getVariableClique(originalVar);
					CliqueNode destination = ct_enlarge.getFamilyClique(copyVar);

					for (DataCase dataCase : data.getData()) {
						CliqueTree ctRepository = _ctps.get(dataCase).getCliqueTree();

						ct_enlarge.copyInMsgsFrom(ctRepository);
						ct_enlarge.copyFuncsFrom(ctRepository, originalVar);
						ctp_enlarge.sendMessage(source, destination);

						CliqueTreePropagation ctp_ = template_.clone();
//						ctp_.getCliqueTree().copyInMsgsFrom(ct_enlarge);

						HashSet<MessagesForLocalEM> msgs = ctp_.getCliqueTree().copyInMsgsNodeFrom(ct_enlarge);			
						repository.put(dataCase, msgs);
					}

					// Local EM start off
					_localEmLearner.setMutableVars(families);
					_localEmLearner.setTemplateCtp(template_);
					_localEmLearner.setRepository(repository);
					_localEmLearner.setThreshold(_EMthreshold);
					model_ = (LTM) _localEmLearner.em(model_, data);

					// break the mapping.
					repository.clear();

					if (model_.getBICScore(data) > optimal) {
						optimal = model_.getBICScore(data);
						info[1] = originalVar;
						info[2] = copyVar;
					}
					
					if(model_.getBICScore(data) > local_optimal)
					{
						local_optimal = model_.getBICScore(data);
					}else
					{
						break;
					}

					addModel2SortedListBIC(model_, sortedModelList, data);
				}
			}
		}	
			
		return sortedModelList.isEmpty() ? null : sortedModelList.get(0);
	}

	/**
	 * Re-consider the structure of model, for each manifest variable node, connect it to its nearest latent node.
	 * 
	 * @param model
	 * @param data
	 * @return
	 */
	
	public LTM reconsiderStructure(LTM model, DataSet data, Map<Variable, Variable> map)
	{
		System.out.println("Begin to do structure adjustment! This may take a while, please wait....");
		
		for(Variable manifest : map.keySet())
		{
			Variable parent = ((BeliefNode)(model.getNode(manifest).getParent())).getVariable();
			Variable nearestLatentVar = map.get(manifest);
			
			if(model.getNode(nearestLatentVar) == null) // it is possible that the nearestLatentVar is already deleted.
			{
				continue;
			}
			
			model.removeEdge(model.getNode(manifest).getEdge(model.getNode(parent)));
			model.addEdge(model.getNode(manifest), model.getNode(nearestLatentVar)); 
			
			//if parent becomes leaf node, delete it; 
			//if parent node only has one child left, attach child to parent node of parent and delete parent node.
			if(model.getNode(parent).getNeighbors().size() <= 2)
			{
				BeliefNode parentParent = (BeliefNode)model.getNode(parent).getParent();
				
				if(parentParent == null)
				{// it is root node
					if(model.getNode(parent).getChildren().size() == 1)
					{//only one latent child. delete it derectly.
						model.removeNode(model.getNode(parent));
					}else
					{//one latent child, one manifest child.
						BeliefNode manifestChild = null;
						BeliefNode latentChild = null;
						
						for(DirectedNode node : model.getNode(parent).getChildren())
						{
							if(((BeliefNode)node).isLeaf())
							{
								manifestChild = (BeliefNode)node;
							}else
							{
								latentChild = (BeliefNode) node;
							}
						}
						
						model.removeEdge(model.getNode(manifestChild.getVariable()).getEdge(model.getNode(parent)));
						model.addEdge(model.getNode(manifestChild.getVariable()), model.getNode(latentChild.getVariable()));
						model.removeNode(model.getNode(parent));
					}
					
				}else
				{//it is not root, it has one parent, one child
					BeliefNode child = (BeliefNode)model.getNode(parent).getChildren().iterator().next();
					
					model.removeEdge(model.getNode(parent).getEdge(child));
					model.addEdge(child, parentParent);
					model.removeNode(model.getNode(parent));	
				}
			}
			
			System.out.println("Relocate manifest variable: "+ manifest.getName()+" from "+ parent.getName()+" to "+nearestLatentVar.getName());
			
			if(!model.isModelModified())
			{
				model.setModelModified(true);
			}
				
		}
		
		System.out.println("End of structure adjustment!");
		
		return model;
	}
	
	/**
	 * Find all the latent nodes which will bring improvements (higher BIC score) after SI. For these latent
	 * nodes, increase their cardinality. Return the result model.
	 * 
	 * @param model
	 * @param data
	 * @return
	 */
	private LTM reconsiderCardinality(LTM model, DataSet data, Map<Variable, Variable> map) 
	{

		Map<Variable, Integer> variablesNeedSI = new HashMap<Variable,Integer>();
		//ArrayList<Variable> variablesNeedSI = new ArrayList<Variable>();
		double baseBICScore = model.getBICScore(data);

		setLocalEMLearner(false);
		System.out.println("  Begin to do all SI in one time! This may take a while, please wait...");

		for (Variable latVar : model.getLatVars()) 
		{
			
		 int cardinality = 1;
		 double previousBIC = Double.NEGATIVE_INFINITY;
		 while(true)
		 {
			// Case 1: Skip if the latVar has got enough states already.
			if (latVar.getCardinality() >= model.getNode(latVar)
					.computeMaxPossibleCardInHLCM())
				break;

			// Preparation
			ArrayList<Variable> children = new ArrayList<Variable>();
			for (DirectedNode node : model.getNode(latVar).getChildren()) {
				children.add(((BeliefNode) node).getVariable());
			}
			int nOfChildren = children.size();
			Variable parent = null;
			if (!model.getNode(latVar).isRoot())
				parent = ((BeliefNode) model.getNode(latVar).getParent())
						.getVariable();

			// Case 2: Otherwise.
			Variable newVar = new Variable(latVar.getCardinality() + cardinality);
			LTM model_ = model.manipulation_VariableReplacement(latVar,
					newVar);

			// Construct repository for localEM
			Map<DataCase, Set<MessagesForLocalEM>> repository = new HashMap<DataCase, Set<MessagesForLocalEM>>();
			Variable[] families = new Variable[nOfChildren + 1];
			for (int i = 0; i < nOfChildren; i++) {
				families[i] = children.get(i);
			}
			families[nOfChildren] = newVar;
			Variable[] variables = new Variable[] { newVar };
			CliqueTreePropagation template_ = new CliqueTreePropagation(model_,
					families, variables);
			template_.getCliqueTree().prune();

			Variable[] variables_enlarge = variables;
			if (parent != null)
				variables_enlarge = new Variable[] { newVar, parent };
			CliqueTreePropagation ctp_enlarge = new CliqueTreePropagation(
					model_, families, variables_enlarge);
			CliqueTree ct_enlarge = ctp_enlarge.getCliqueTree();
			ct_enlarge.prune();
			CliqueNode source = null;
			CliqueNode destination = null;
			if (parent != null) {
				source = ct_enlarge.getVariableClique(parent);
				destination = ct_enlarge.getFamilyClique(newVar);
			}

			CliqueTreePropagation ctp = new CliqueTreePropagation(model);
			
			for (DataCase dataCase : data.getData()) 
			{
				CliqueTreePropagation copy = ctp.clone();
				copy.setEvidence(data.getVariables(), dataCase.getStates());
				copy.propagate();
				
				CliqueTree ctRepository = copy.getCliqueTree();			
//				CliqueTree ctRepository = _ctps.get(dataCase).getCliqueTree();

				ct_enlarge.copyInMsgsFrom(ctRepository);
				if (parent != null) {
					CliqueNode tail = ctRepository.getVariableClique(parent);
					CliqueNode head = ctRepository.getFamilyClique(latVar);
					Function message = tail.getMessageTo(head);
					double normalization = tail.getNormalizationTo(head);
					source.setMessageTo(destination, message);
					source.setNormalizationTo(destination, normalization);
				}

				CliqueTreePropagation ctp_ = template_.clone();
				HashSet<MessagesForLocalEM> msgs = ctp_.getCliqueTree().copyInMsgsNodeFrom(ct_enlarge);

				repository.put(dataCase, msgs);
			}

			long start = System.currentTimeMillis();

			// Local EM start off
			_localEmLearner.setMutableVars(families);
			_localEmLearner.setTemplateCtp(template_);
			_localEmLearner.setRepository(repository);
			model_ = (LTM) _localEmLearner.em(model_, data);

			System.out.println("=== Local EM in SI, Elapsed Time: "+ (System.currentTimeMillis() - start) + " ms ===");

			repository.clear();

			System.out.println("    SI: (" + latVar.getName() + ") BIC=" + model_.getBICScore(data) + ";  LL=" + model_.getLoglikelihood(data));

			if (model_.getBICScore(data) - baseBICScore > _BICthreshold && model_.getBICScore(data) - previousBIC > _BICthreshold) 
//			if (model_.getBICScore(data) - baseBICScore > CompareModelThreshold && model_.getBICScore(data) - previousBIC > CompareModelThreshold) 
			{
				//Keep a record of latent nodes which could bring improvements after SI.
				variablesNeedSI.put(latVar, cardinality);
				cardinality++;
				previousBIC = model_.getBICScore(data);
			}else
			{
				break;
			}
		 }
		}

		LTM bestModel = model.clone();

		//Increase the cardinality of latent nodes
		for(Variable latVar : variablesNeedSI.keySet())
		{
			 Variable newVar = new Variable(latVar.getCardinality() + variablesNeedSI.get(latVar));
			 bestModel = bestModel.manipulation_VariableReplacement(latVar, newVar);
			 
			 System.out.println("SI: "+latVar.getName()+"("+latVar.getCardinality()+") ->"+newVar.getName()+"("+newVar.getCardinality()+")");
			 
			 for(Variable var : map.keySet())
			 {
				 if(map.get(var)== latVar)
				 {
					 map.put(var, newVar);
				 }
			 }
		
			 if(!bestModel.isModelModified())
			 {
				 bestModel.setModelModified(true);
			 }
		
		} 
		
		//System.out.println("The BIC score for this run: " + bestModel.getBICScore(data));

		return bestModel;

	}

	/**
	 * Generate candidate models and evaluate them. We will return (1) null if
	 * there is no candidate model (2) the best model according to our
	 * evaluation.
	 * 
	 * @param model
	 * @param data
	 * @return
	 */
	public LTM bestModelSI(LTM model, DataSet data) {
		ArrayList<LTM> sortedModelList = new ArrayList<LTM>();
		setLocalEMLearner(false);

		for (Variable latVar : model.getLatVars()) 
		{
			System.gc();

			// Case 1: Skip if the latVar has got enough states already.
			if (latVar.getCardinality() > model.getNode(latVar).computeMaxPossibleCardInHLCM())
			{
//				System.out.println("Latent Variable:"+latVar.getName()+"("+latVar.getCardinality()+") got enough states!");
				continue;
			}

			
			// Preparation
			ArrayList<Variable> children = new ArrayList<Variable>();
			for (DirectedNode node : model.getNode(latVar).getChildren()) 
			{
				children.add(((BeliefNode) node).getVariable());
			}
			int nOfChildren = children.size();
			Variable parent = null;
			if (!model.getNode(latVar).isRoot())
				parent = ((BeliefNode) model.getNode(latVar).getParent()).getVariable();

			// Case 2: Otherwise.
			Variable newVar = new Variable(latVar.getCardinality() + 1);
			LTM model_ = model.manipulation_VariableReplacement(latVar, newVar);

			
			// Construct repository for localEM
			Map<DataCase, Set<MessagesForLocalEM>> repository = new HashMap<DataCase, Set<MessagesForLocalEM>>();
			Variable[] families = new Variable[nOfChildren + 1];
			for (int i = 0; i < nOfChildren; i++) {
				families[i] = children.get(i);
			}
			families[nOfChildren] = newVar;
			Variable[] variables = new Variable[] { newVar };
			CliqueTreePropagation template_ = new CliqueTreePropagation(model_,families, variables);
			template_.getCliqueTree().prune();

			Variable[] variables_enlarge = variables;
			if (parent != null)
				variables_enlarge = new Variable[] { newVar, parent };
			CliqueTreePropagation ctp_enlarge = new CliqueTreePropagation(
					model_, families, variables_enlarge);
			CliqueTree ct_enlarge = ctp_enlarge.getCliqueTree();
			ct_enlarge.prune();
			CliqueNode source = null;
			CliqueNode destination = null;
			if (parent != null) {
				source = ct_enlarge.getVariableClique(parent);
				destination = ct_enlarge.getFamilyClique(newVar);
			}

			for (DataCase dataCase : data.getData()) {
				CliqueTree ctRepository = _ctps.get(dataCase).getCliqueTree();

				ct_enlarge.copyInMsgsFrom(ctRepository);
				if (parent != null) {
					CliqueNode tail = ctRepository.getVariableClique(parent);
					CliqueNode head = ctRepository.getFamilyClique(latVar);
					Function message = tail.getMessageTo(head);
					double normalization = tail.getNormalizationTo(head);
					source.setMessageTo(destination, message);
					source.setNormalizationTo(destination, normalization);
				}

				CliqueTreePropagation ctp_ = template_.clone();
//				ctp_.getCliqueTree().copyInMsgsFrom(ct_enlarge);

				HashSet<MessagesForLocalEM> msgs = ctp_.getCliqueTree().copyInMsgsNodeFrom(ct_enlarge);
				repository.put(dataCase, msgs);
			}
			
			// Local EM start off
			_localEmLearner.setMutableVars(families);
			_localEmLearner.setTemplateCtp(template_);
			_localEmLearner.setRepository(repository);
			model_ = (LTM) _localEmLearner.em(model_, data);

			repository.clear();

//			setEMLearner();
//			model_ = (LTM) _emLearner.em(model_, data);
			
//			System.out.println("    SI: (" + latVar.getName() + ") BIC="
//					+ model_.getBICScore(data) + ";  LL="
//					+ model_.getLoglikelihood(data));

			addModel2SortedListBIC(model_, sortedModelList, data);
		}
		return sortedModelList.isEmpty() ? null : sortedModelList.get(0);
	}

	/**
	 * Generate candidate models and evaluate them. We will return (1) null if
	 * there is no candidate model (2) the best model according to our
	 * evaluation.
	 * 
	 * @return
	 */
	
	public LTM bestModelRestriNR(LTM model, Variable parent, Variable destinationVar, DataSet data) 
	{
		ArrayList<LTM> sortedModelList = new ArrayList<LTM>();
		// Note the reuse sign
		setLocalEMLearner(true);
		model = model.clone();
//		System.out.println("    RNR: (alterVar.name, parent.name, destinationVar.name)");

		LTM equiModel = model.changeRoot(destinationVar);
//		computeCtps4EquiHLCM(model, equiModel, data);
		computeCtps(equiModel, data);
		model = equiModel;

		for (AbstractNode alterNode : model.getNode(parent).getChildren()) 
		{	
//			System.gc();
			Variable alterVar = ((BeliefNode) alterNode).getVariable();
			
			//Only consider the most possible one
//			if(!alterVar.equals(closestVar))
//				continue;
			
			// Preparation
			ArrayList<Variable> vAlter2Root = new ArrayList<Variable>();
			for (BeliefNode node : model.path2Root(model.getNode(alterVar))) {
				vAlter2Root.add(node.getVariable());
			}

			// Make the candidate model.
			LTM model_ = null;
			
			if(model.getNode(parent).getChildren().size()<= 2)
			{
				//if parent node has only two children, then relocate all.
				model_ = model.manipulation_NodeRelocation(alterVar);
				
				for(AbstractNode onlyChild : model_.getNode(parent).getChildren())
				{
					Variable Child= ((BeliefNode) onlyChild).getVariable();
					model_ = model_.manipulation_NodeRelocation(Child);
				}
				
				if(model_.getLatVars().size() == 1)
				{
					setEMLearner();
					model_ = (LTM) _emLearner.em(model_, data);
					addModel2SortedListBIC(model_, sortedModelList, data);
					break;
				}
				
			}else
			{
				model_ = model.manipulation_NodeRelocation(alterVar);
			}

			Function cpt = computeSuffStats(data, alterVar, destinationVar);
			cpt.normalize(alterVar);
			model_.getNode(alterVar).setCpt(cpt);

			BeliefNode cParentNode = model_.getNode(parent);
			// Boundary Case 1
			if (cParentNode.isLeaf()) {
				model_.removeNode(cParentNode);
				vAlter2Root.remove(cParentNode.getVariable());
			}
			// Boundary Case 2
			if (cParentNode.getDegree() == 2) {
				BeliefNode cParentParent = (BeliefNode) cParentNode.getParent();
				BeliefNode cParentChild = (BeliefNode) cParentNode
						.getChildren().iterator().next();
				if (cParentNode.getVariable().getCardinality() >= cParentParent
						.getVariable().getCardinality()
						|| cParentNode.getVariable().getCardinality() >= cParentChild
								.getVariable().getCardinality()) {
					Function f1 = cParentNode.getCpt();
					Function f2 = cParentChild.getCpt();
					Function newCpt = f1.times(f2).sumOut(parent);
					newCpt.normalize(cParentChild.getVariable());
					model_.removeNode(cParentNode);
					model_.addEdge(cParentChild, cParentParent);
					cParentChild.setCpt(newCpt);

					vAlter2Root.remove(parent);
					vAlter2Root.add(cParentChild.getVariable());
				}
			}
			if (!model_.isCardRegular(alterVar))
				continue;

			// Construct repository for localEM
			Map<DataCase, Set<MessagesForLocalEM>> repository = new HashMap<DataCase, Set<MessagesForLocalEM>>();
			Variable[] families = new Variable[] { alterVar };
			Variable[] variables = new Variable[0];
			CliqueTreePropagation template_ = new CliqueTreePropagation(model_,
					families, variables);
			template_.getCliqueTree().prune();

			Variable[] families_enlarge = vAlter2Root
					.toArray(new Variable[vAlter2Root.size()]);
			vAlter2Root.remove(alterVar);
			Variable[] variables_enlarge = vAlter2Root
					.toArray(new Variable[vAlter2Root.size()]);
			CliqueTreePropagation ctp_enlarge = new CliqueTreePropagation(
					model_, families_enlarge, variables_enlarge);
			CliqueTree ct_enlarge = ctp_enlarge.getCliqueTree();
			ct_enlarge.prune();

			CliqueNode source = ct_enlarge.getVariableClique(destinationVar);
			CliqueNode destination = ct_enlarge.getFamilyClique(alterVar);

			for (DataCase dataCase : data.getData()) {
				CliqueTree ctRepository = _ctps.get(dataCase).getCliqueTree();

				ct_enlarge.copyInMsgsFrom(ctRepository);
				ctp_enlarge.setEvidence(data.getVariables(), dataCase
						.getStates());
				ctp_enlarge.absorbEvidence();
				ctp_enlarge.collectMessage(source, destination);

				CliqueTreePropagation ctp_ = template_.clone();
				//ctp_.getCliqueTree().copyInMsgsFrom(ct_enlarge);
				
				HashSet<MessagesForLocalEM> msgs = ctp_.getCliqueTree().copyInMsgsNodeFrom(ct_enlarge);

				repository.put(dataCase, msgs);
			}

			// Local EM start off
			_localEmLearner.setMutableVars(families);
			_localEmLearner.setTemplateCtp(template_);
			_localEmLearner.setRepository(repository);
			model_ = (LTM) _localEmLearner.em(model_, data);

			repository.clear();
			
//			setEMLearner();
//			model_ = (LTM) _emLearner.em(model_, data);

//			System.out.println("    RNR: (" + alterVar.getName() + ", "
//					+ parent.getName() + ", " + destinationVar.getName()
//					+ ") BIC=" + model_.getBICScore(data) + ";  LL="
//					+ model_.getLoglikelihood(data));
			addModel2SortedListBIC(model_, sortedModelList, data);
		}
		return sortedModelList.isEmpty() ? null : sortedModelList.get(0);
	}

	/**
	 * For each datacase, compute the corresponding ct after propogation. Note
	 * that the clique tree is constructed for HLCM rather than BayesNet.
	 * 
	 * @param model
	 *            The HLCM
	 * @param data
	 *            A collection of DataCases
	 * @return
	 */
	public void computeCtps(LTM model, DataSet data) {
		_ctps.clear();
		CliqueTreePropagation ctp = new CliqueTreePropagation(model);
		
		double loglikelihood = 0.0;
		
		for (DataCase dataCase : data.getData()) {
			CliqueTreePropagation copy = ctp.clone();
			copy.setEvidence(data.getVariables(), dataCase.getStates());
			double likelihood = copy.propagate();
			
			loglikelihood += Math.log(likelihood) * dataCase.getWeight();
			
			// clean up the clique tree
			for (AbstractNode node : copy.getCliqueTree().getNodes()) {
				CliqueNode cNode = (CliqueNode) node;
				// cNode.clearFunctions();
				cNode.clearQualifiedNeiMsgs();
				cNode.setMsgsProd(null);
			}
			_ctps.put(dataCase, copy);
		}
		
		model.setLoglikelihood(data, loglikelihood);
	}

	/**
	 * Compute the unit increase and set this value in the UI field of model, it
	 * is supposed that m is always complex than benchmark model. Pay more
	 * attention to the case that BIC decreased.
	 * 
	 * @param benchmarkModel
	 *            Current model serves as the benchmark
	 * @param m
	 *            We suppose that log likelihood of the data has been stored
	 * @param data
	 * @return Return the unit increase of model m in terms of the benchmark
	 *         model.
	 */
	private double computeUI(LTM benchmarkModel, LTM m, DataSet data) {
		// Make sure that DimDif is positive always.
		double BICDif = m.getBICScore(data) - benchmarkModel.getBICScore(data);
		double DimDif = m.computeDimension()
				- benchmarkModel.computeDimension();
		double UI;

		if (BICDif > 0) {
			UI = BICDif / DimDif;
			m.setImprovement(UI);
			return UI;
		} else {
			UI = DimDif / BICDif;
			m.setImprovement(UI);
			return UI;
		}
	}

	/**
	 * Add newModel to sortedModelArrayList to the appropriate location so that
	 * the list remain sorted (according to model score) afterwards. They are
	 * compared according to BIC score.
	 */
	private void addModel2SortedListBIC(LTM newModel,
			ArrayList<LTM> sortedModelArrayList, DataSet data) {

		// find the position of the first model whose score is smaller than
		// newModel
		int pos = 0;
		Iterator<LTM> iter = sortedModelArrayList.iterator();
		while (iter.hasNext()) {
			LTM model = iter.next();
			if (model.getBICScore(data) < newModel.getBICScore(data))
				break;
			pos++;
		}
		// add newModel and newNode to the lists at the position pos
		sortedModelArrayList.add(pos, newModel);

		int size = sortedModelArrayList.size();
		if (size > _length) {
			for (int i = size - 1; i >= _length; i--)
				sortedModelArrayList.remove(i);
		}
	}



	/**
	 * We compute the posterior sufficient statistics for the corresponding two
	 * variables based on the already-computed ctps.
	 * 
	 * Note: Sometimes the var1 or var2 maybe hidden or maybe observed. It
	 * dependens on the dataCase. However, the return should always be a
	 * function of (var1, var2).
	 * 
	 * @param data
	 * @param var1
	 * @param var2
	 * @return
	 */
	private Function computeSuffStats(DataSet data, Variable var1, Variable var2) {

		LTM model = (LTM) _ctps.values().iterator().next().getBayesNet();
		ArrayList<BeliefNode> bNodesOnPath = model.computePath(var1, var2);

		Set<Variable> queryVars = new HashSet<Variable>();
		queryVars.add(var1);
		queryVars.add(var2);

		Function suffStats = null;
		for (DataCase dataCase : data.getData()) {
			CliqueTreePropagation ctp = _ctps.get(dataCase);
			CliqueTree ct = ctp.getCliqueTree();

			Set<CliqueNode> CliqueSubTree = new HashSet<CliqueNode>();
			for (BeliefNode bNode : bNodesOnPath) {
				CliqueNode variable = ct.getVariableClique(bNode.getVariable());
				CliqueNode family = ct.getFamilyClique(bNode.getVariable());
				if (variable != null) {
					CliqueSubTree.add(variable);
				}
				CliqueSubTree.add(family);
			}

			Function fracWeight = ctp.computeBelief(queryVars, CliqueSubTree);

			fracWeight.multiply(dataCase.getWeight());

			if (suffStats == null)
				suffStats = fracWeight;
			else
				suffStats.plus(fracWeight);
		}
		return suffStats;
	}
	
//	Map<Variable, Map<Variable, Double>> _mis = null;
//	
//	public void setMutualInfoSet(Map<Variable, Map<Variable, Double>> mis)
//	{
//		_mis = mis;
//	}
}