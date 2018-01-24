package tm.hlta

import org.latlab.learner.SparseDataSet
import org.latlab.util.DataSet
import tm.util.Reader
import org.latlab.model.LTM

object ProgressiveEmBuilder {
  
  /**
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
  
  /**
   * Parameter follows the suggested numbers in cluster.StepwiseEMHLTA
   */
  def apply(data: SparseDataSet, modelName: String, emMaxStep: Int = 50, emNumRestart: Int = 3, emThreshold: Double = 0.01,
      udThreshold: Int = 3, maxIsland: Int = 15, maxTop: Int = 30, sizeBatch: Int = 500, maxEpochs: Int = 10, 
      globalMaxEmStep: Int = 100, sizeFirstBatch: Option[Int] = None, firstBatchUseAll: Boolean = false): LTM = {
    
    val _sizeFirstBatch = if(firstBatchUseAll) "all"
      else if(sizeFirstBatch.isEmpty){
        if(data.getNumOfDatacase > 10000) 5000.toString() else "all"
      }else{          
        sizeFirstBatch.toString()
      }
      
    val builder = new clustering.StepwiseEMHLTA()
    builder.initialize(data, emMaxStep, emNumRestart, emThreshold, udThreshold, modelName, maxIsland, maxTop, sizeBatch, maxEpochs,
          globalMaxEmStep, _sizeFirstBatch)
    builder.IntegratedLearn()
      
    Reader.readModel(modelName+".bif")
  }
}