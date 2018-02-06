package tm.hlta

import org.latlab.learner.SparseDataSet
import org.latlab.util.DataSet
import tm.util.Reader
import org.latlab.model.LTM
import tm.util.Arguments

import org.rogach.scallop._

object ProgressiveEmBuilder {
  
  /**
   * Progressive EM
   * EM parameters:
   * 		emMaxStep: Maximum number of EM steps (e.g. 50).
   * 		emNumRestarts: Number of restarts in EM (e.g. 5).
   * 		emThreshold: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
   * 		udThreshold: The threshold used in unidimensionality test for constructing islands (e.g. 3).
   * 		maxIsland: Maximum number of variables in an island (e.g. 10).
   * 		maxTop: Maximum number of variables in top level (e.g. 15).
   * 
   * Parameter follows the suggested numbers in clustering.PEM
   */
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: ProgressiveEmBuilder [OPTION]... dataFile emMaxStep name
             |E.g. ProgressiveEmBuilder data.arff 50 model1
             |The output file will be model1.bif""")
             
    val dataFile = trailArg[String]()
    val emMaxStep = trailArg[Int](descr = "Maximum number of EM steps (e.g. 50)")
    val outputName = trailArg[String]()
    
    val emNumRestart = opt[Int](descr = "Number of restarts in EM (e.g. 5)", default = Some(5))
    val emThreshold = opt[Double](descr = "Threshold of improvement to stop EM (e.g. 0.01)", default = Some(0.01))
    val udThreshold = opt[Double](descr = "The threshold used in unidimensionality test for constructing islands (e.g. 3)", default = Some(3))
    val maxIsland = opt[Int](descr = "Maximum number of variables in an island (e.g. 10)", default = Some(10))
    val maxTop = opt[Int](descr = "Maximum number of variables in top level (e.g. 15)", default = Some(15))

    verify
    checkDefaultOpts()
  }
  
  def main(args: Array[String]){
    val conf = new Conf(args)
    
    val data = Reader.readData(conf.dataFile())
    val builder = new clustering.PEM()
    builder.initialize(data.toHlcmDataSet(), conf.emMaxStep(), conf.emNumRestart(), conf.emThreshold(), 
        conf.udThreshold(), conf.outputName(), conf.maxIsland(), conf.maxTop())
    builder.IntegratedLearn()
  }
  
  /**
   * For external call
   * Parameter follows the suggested numbers in clustering.PEM
   */
  def apply(data: DataSet, modelName: String, emMaxStep: Int = 50, emNumRestart: Int = 3, emThreshold: Double = 0.01, 
      udThreshold: Double = 3, maxIsland: Int = 15, maxTop: Int = 20): LTM = {
    
    val builder = new clustering.PEM()
    builder.initialize(data, emMaxStep, emNumRestart, emThreshold, udThreshold, modelName, maxIsland, maxTop)
    builder.IntegratedLearn()
    
    Reader.readModel(modelName+".bif")
  }

}

object StepwiseEmBuilder {
  
  /**Stepwise EM
   * Local EM parameters:
   * 		emMaxSteps: Maximum number of EM steps (e.g. 50).
   * 		emNumRestarts: Number of restarts in EM (e.g. 5).
   * 		emThreshold: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
   * 		udThreshold: The threshold used in unidimensionality test for constructing islands (e.g. 3).
   * 		maxIsland: Maximum number of variables in an island (e.g. 10).
   * 		maxTop: Maximum number of variables in top level (e.g. 15).
   * Global parameters:
   * 		globalBatchSize: Number of data cases used in each stepwise EM step (e.g. 1000).
   * 		globalMaxEpochs: Number of times the whole training dataset has been gone through (e.g. 10).
   * 		globalMaxEmSteps: Maximum number of stepwise EM steps (e.g. 128).
   * 		structBatchSize: Number of data cases used for building model structure.
   * 
   * Parameter follows the suggested numbers in cluster.StepwiseEMHLTA
   */
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: StepwiseEmBuilder [OPTION]... dataFile emMaxStep name
             |E.g. ProgressiveEmBuilder data.arff 50 model1
             |The output file will be model1.bif""")
             
    val dataFile = trailArg[String]()
    val emMaxStep = trailArg[Int](descr = "Maximum number of EM steps (e.g. 50)")
    val outputName = trailArg[String]()
    
    val emNumRestart = opt[Int](descr = "Number of restarts in EM (e.g. 5)", default = Some(5))
    val emThreshold = opt[Double](descr = "Threshold of improvement to stop EM (e.g. 0.01)", default = Some(0.01))
    val udThreshold = opt[Double](descr = "The threshold used in unidimensionality test for constructing islands (e.g. 3)", default = Some(3))
    val maxIsland = opt[Int](descr = "Maximum number of variables in an island (e.g. 10)", default = Some(10))
    val maxTop = opt[Int](descr = "Maximum number of variables in top level (e.g. 15)", default = Some(15))
    
    val globalBatchSize = opt[Int](descr = "Number of data cases used in each stepwise EM step", default = Some(1000))
    val globalMaxEpochs = opt[Int](descr = "Number of times the whole training dataset has been gone through (e.g. 10)", default = Some(10))
    val globalMaxEmSteps = opt[Int](descr = "Maximum number of stepwise EM steps (e.g. 128)", default = Some(128))
    
    val structBatchSize = opt[Int](descr = "Number of data cases used for building model structure", default = None)
    val structUseAll = opt[Boolean](descr = "Use all data cases for building model structure", default = Some(false))

    verify
    checkDefaultOpts()
  }
  
  def main(args: Array[String]){
    val conf = new Conf(args)
    
    val data = Reader.readData(conf.dataFile())
    
    val _sizeFirstBatch = if(conf.structUseAll()) "all"
      else if(conf.structBatchSize.isEmpty){//auto determine structBatchSize
        if(data.size > 10000) 5000.toString() else "all"
      }else{          
        conf.structBatchSize().toString()
      }
      
    val builder = new clustering.StepwiseEMHLTA()
    builder.initialize(data.toTupleSparseDataSet(), conf.emMaxStep(), conf.emNumRestart(), conf.emThreshold(), conf.udThreshold(), conf.outputName(), 
        conf.maxIsland(), conf.maxTop(), conf.globalBatchSize(), conf.globalMaxEpochs(), conf.globalMaxEmSteps(), _sizeFirstBatch)
    builder.IntegratedLearn()
  }
  
  /**
   * Parameter follows the suggested numbers in cluster.StepwiseEMHLTA
   */
  def apply(data: SparseDataSet, modelName: String, emMaxStep: Int = 50, emNumRestart: Int = 3, emThreshold: Double = 0.01,
      udThreshold: Int = 3, maxIsland: Int = 15, maxTop: Int = 30, globalBatchSize: Int = 500, globalMaxEpochs: Int = 10, 
      globalMaxEmSteps: Int = 100, structBatchSize: Option[Int] = None, structBatchAll: Boolean = false): LTM = {
    
    val _sizeFirstBatch = if(structBatchAll) "all"
      else if(structBatchSize.isEmpty){//auto determine structBatchSize
        if(data.getNumOfDatacase > 10000) 5000.toString() else "all"
      }else{          
        structBatchSize.toString()
      }
      
    val builder = new clustering.StepwiseEMHLTA()
    builder.initialize(data, emMaxStep, emNumRestart, emThreshold, udThreshold, modelName, maxIsland, maxTop, globalBatchSize, globalMaxEpochs,
          globalMaxEmSteps, _sizeFirstBatch)
    builder.IntegratedLearn()
      
    Reader.readModel(modelName+".bif")
  }
}