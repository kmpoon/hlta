
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.latlab.util.Variable;

public class EmpiricalMiComputerForBinaryData{
	private final DataSet data;
	private final List<Variable> variables;
//	private final int[] idMappingFromDataToVariables;

	public EmpiricalMiComputerForBinaryData(DataSet data,
			List<Variable> variables) {
		this.data = data;
		this.variables = variables;

	/*	idMappingFromDataToVariables = new int[variables.size()];
		Arrays.fill(idMappingFromDataToVariables, -1);
		for (int i = 0; i < variables.size(); i++) {
			int idInData =
					Arrays.binarySearch(data.getVariables(), variables.get(i));
			idMappingFromDataToVariables[idInData] = i;
		}*/
	}

	/**
	 * Computes a the mutual information between each pair of variables. It does
	 * not contain any valid value on the diagonal.
	 * 
	 * @param includeClassVariable
	 *            whether to include the class variable
	 * @return mutual information for each pair of variables
	 */
	public ArrayList<double[]> computerPairwise() {
		int numberOfVariables = variables.size();
		double totalWeight = data.getTotalWeight();
		ArrayList<double[]> f = new FrequencyCounter().computeSequential();
		ArrayList<double[]> results = new ArrayList<double[]>(numberOfVariables);
        

		for(int i = 0; i<numberOfVariables;i++){
			results.add(new double[numberOfVariables]);
		}
		
		for (int i = 0; i < numberOfVariables; i++) {
			for (int j = i + 1; j < numberOfVariables; j++) {
				double[] pi = getMarginal(f.get(i)[i] / totalWeight);
				double[] pj = getMarginal(f.get(j)[j]/ totalWeight);

				double[][] pij = new double[2][2];
				pij[1][1] = f.get(i)[j] / totalWeight;
				pij[1][0] = pi[1] - pij[1][1];
				pij[0][1] = pj[1] - pij[1][1];
				pij[0][0] = 1 - pi[1] - pj[1] + pij[1][1];

				double mi = 0;
				for (int xi = 0; xi < 2; xi++) {
					for (int xj = 0; xj < 2; xj++) {
						if (pij[xi][xj] > 0) {
							mi +=
									pij[xi][xj]
											* Math.log(pij[xi][xj]
													/ (pi[xi] * pj[xj]));
						}
					}
				}

				
					results.get(i)[j] = mi;
					results.get(j)[i] = mi;
				assert !Double.isNaN(mi);
			}
		}
		return results;
	}

	private double[] getMarginal(double p_1) {
		double[] result = { 1 - p_1, p_1 };
		return result;
	}

	private class FrequencyCounter {
		private FrequencyCounter() {
		}

		public ArrayList<double[]> computeParallel() {
			ParallelComputation c =
					new ParallelComputation(0, data.getNumberOfEntries());
			ForkJoinPool pool = new ForkJoinPool();
			pool.invoke(c);
			return c.frequencies;
		}

		private ArrayList<double[]> computeSequential() {
			return computeFrequencies(0, data.getNumberOfEntries());
		}

		protected ArrayList<double[]> computeFrequencies(int start, int end) {
			// the diagonal entries contain the frequencies of a single variable
			ArrayList<double[]> frequencies =
					new ArrayList<double[]>(variables.size());

			for(int i = 0; i<variables.size();i++){
				frequencies.add(new double[variables.size()]);
			}

			System.out.println("Initialized the map");

			
			ArrayList<DataCase> cases = data.getData();
			for (int caseIndex = start; caseIndex < end; caseIndex++) {
				DataCase c = cases.get(caseIndex);
				int[] states = c.getStates();
				double weight = c.getWeight();

				// find the indices of states that are greater than zero
				List<Integer> entries = new ArrayList<>(states.length);
				for (int s = 0; s < states.length; s++) {
					if (states[s] > 0) {
						entries.add(s);
					}
				}

				// update the single and joint counts
				for (int i : entries) {
				//	int iInVariables = idMappingFromDataToVariables[i];
					int iInVariables = i;
					if (iInVariables < 0)
						continue;

					for (int j : entries) {
						//int jInVariables = idMappingFromDataToVariables[j];
						int jInVariables = j;
						if (jInVariables < 0)
							continue;
						
			
						double freq = frequencies.get(iInVariables)[jInVariables];
						freq += weight;
						frequencies.get(iInVariables)[jInVariables]=freq;
						
					}
				}
			}

			return frequencies;
		}

		@SuppressWarnings("serial")
		private class ParallelComputation extends RecursiveAction {

			private final int start;
			private final int end;
			private static final int THRESHOLD = 500;
			private ArrayList<double[]> frequencies;

			private ParallelComputation(int start, int end) {
				this.start = start;
				this.end = end;
			}

			private void computeDirectly() {
				frequencies = computeFrequencies(start, end);
			}

			@Override
			protected void compute() {
				int length = end - start;
				if (length <= THRESHOLD) {
					computeDirectly();
					return;
				}

				int split = length / 2;
				ParallelComputation c1 =
						new ParallelComputation(start, start + split);
				ParallelComputation c2 =
						new ParallelComputation(start + split, end);
				invokeAll(c1, c2);

				// This is not very efficient for combining the results
				// from subtasks.
				frequencies = c1.frequencies;
				for (int i = 0; i < frequencies.size();i++) {
					for (int j = 0; j < frequencies.get(i).length; j++) {
						double t1 = frequencies.get(i)[j];
						double t2 = c2.frequencies.get(i)[j];
						frequencies.get(i)[j] = t1+t2;
					}
				}
			}
		}
	}

}
