package tm.hlta

import org.latlab.learner.ParallelEmLearner
import org.latlab.model.LTM
import org.latlab.util.DataSet
import org.latlab.model.BeliefNode

import collection.JavaConversions._

object RunEM {
  def main(args: Array[String]) {
    
    if (args.length < 2) {
      printUsage()
    } else {
      run(args(0), args(1))
    }
    
    def printUsage() = {
      println("RunEM model_file data_file")
    }
    
    def run(modelFile: String, dataFile: String) = {
      val (model, data) = Reader.readLTMAndData(modelFile, dataFile)
      val modelAfterEM = em(model, data)
      
      val outputName = modelFile.replaceAll(".bif$", ".em.bif")
      modelAfterEM.saveAsBif(outputName)
    }

    def em(model: LTM, data: DataSet) = {
      val emLearner = new ParallelEmLearner();
      emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
      emLearner.setMaxNumberOfSteps(50);
      emLearner.setNumberOfRestarts(5);
      emLearner.setReuseFlag(true);
      emLearner.setThreshold(1e-2);

      val modelAfterEM = emLearner.em(model, data).asInstanceOf[LTM]
      smoothParameters(modelAfterEM, data.getTotalWeight);
    }

    def smoothParameters(model: LTM, sampleSize: Double) = {
      model.getNodes.map(_.asInstanceOf[BeliefNode]).foreach { n =>
        val cardinality = n.getVariable.getCardinality
        val values = n.getCpt.getCells
        (0 until values.size).foreach { i =>
          values(i) = (values(i) * sampleSize + 1) / (sampleSize + cardinality)
        }
      }

      model
    }

  }
}