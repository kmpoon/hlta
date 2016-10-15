//package clustering;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.FilenameFilter;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.regex.Pattern;
//
//import org.latlab.learner.CliqueTreePropagationGroup;
//import org.latlab.learner.ParallelEmLearner;
//import org.latlab.model.BayesNet;
//import org.latlab.util.DataSet;
//import org.latlab.util.DataSet.DataCase;
//
///**
// * Number of steps is determined by the number of parts split from a data set.
// * 
// * @author kmpoon
// *
// */
//public class StochasticEm extends ParallelEmLearner {
//
//	private String dataFileName = null;
//
//	/**
//	 * Given {@code dataSet} is not used. It is included for the sake of
//	 * compatibility.
//	 */
//	public BayesNet em(BayesNet bayesNet, DataSet dataSet) {
//		if (dataFileName == null) {
//			throw new IllegalArgumentException("Data file name must be set");
//		}
//
//		_nSteps = 0;
//		double loglikelihood;
//
//		File[] dataPartFiles = getDataPartFiles(dataFileName, _nMaxSteps);
//		for (int i = 0; i < dataPartFiles.length; i++) {
//			try {
//				File file = dataPartFiles[i];
//				System.out.println(String.format(
//						"Running stochastic EM %d-th step with file %s",
//						_nSteps + 1, file.getName()));
//
//				DataSet dataPart = new DataSet(new FileInputStream(file));
//				dataPart = dataPart.synchronize(bayesNet);
//
//				
////				// selects a good starting point
////				CliqueTreePropagationGroup ctps =
////						chickeringHeckermanRestart(bayesNet, dataPart);
////
////				emStep(ctps, dataPart);
////				_nSteps++;
//
//				// selects a good starting point
//				CliqueTreePropagationGroup ctps =
//						CliqueTreePropagationGroup.constructFromModel(
//								bayesNet.clone(),
//								getForkJoinPool().getParallelism());
//
//				 emStep(ctps, dataPart);
//				 _nSteps++;
//
//				bayesNet = ctps.model;
//				loglikelihood = bayesNet.getLoglikelihood(dataPart);
//
//				System.out.println(String.format(
//						"Log-Likelihood after %d-th step: %.4f", _nSteps,
//						loglikelihood));
//
//			} catch (Exception e) {
//				System.out.println("Error in reading data file.");
//				e.printStackTrace();
//			}
//		}
//
//		return bayesNet;
//	}
//
//	public void setDataFileName(String name) {
//		dataFileName = name;
//	}
//
//	private static File[] getDataPartFiles(String originalDataName, int total) {
//		String format = "%s-%d-%03d.txt";
//		String prefix = originalDataName.replace(".txt", "");
//
//		File[] files = new File[total];
//
//		for (int i = 0; i < total; i++) {
//			String filename = String.format(format, prefix, total, i + 1);
//			files[i] = new File(filename);
//
//			if (!files[i].exists()) {
//				throw new IllegalArgumentException("Data part file not found: "
//						+ filename);
//			}
//		}
//
//		return files;
//	}
//}
