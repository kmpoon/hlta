package tm.hlta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.Utils;
import org.latlab.util.Variable;


/**
 * Copied from clustering.StepwiseEMHLTA.java
 * Solely serves scala EM and StepwiseEM method
 * 
 * @author Leung Chun Fai
 *
 */
public class EmMethods {
	
	public static LTM reorderStates(LTM model) {
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
//		for (String str : topLevel) {
//			processName(model, str, topLevelIndex, levelAndIndex, varDiffLevels);
//		}

		return model;
	}
	
	private static LTM reorderStates(LTM bn, HashMap<Integer, HashSet<String>> varDiffLevels) {
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

	private static void processName(BayesNet model, String str, int level,
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

	private static void changeName(BayesNet model, String str, int level,
			HashMap<Integer, Integer> levelAndIndex) {
		BeliefNode var = model.getNodeByName(str);

		int index = levelAndIndex.get(level) + 1;
		String newName = "Z" + level + index;
		var.setName(newName);

		levelAndIndex.put(level, index);
	}

	private static HashMap<Integer, HashSet<String>> processVariables(BayesNet model) {
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

	private static List<Map.Entry<Variable, Double>> SortChildren(Variable var,
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

	private static double computeMI(Variable x, Variable y, CliqueTreePropagation ctp) {
		List<Variable> xyNodes = new ArrayList<Variable>();
		xyNodes.add(x);
		xyNodes.add(y);

		return Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
	}

}
