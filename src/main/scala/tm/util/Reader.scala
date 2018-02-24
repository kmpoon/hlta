package tm.util

import java.io.FileInputStream
import org.latlab.io.bif.BifParser
import org.latlab.model.LTM
import org.latlab.util.DataSet
import weka.core.converters.ConverterUtils.DataSource
import weka.core.Instances
import org.latlab.util.Variable
import collection.JavaConversions._
import weka.core.Attribute
import java.util.ArrayList
import org.slf4j.LoggerFactory
import scala.Range
import java.nio.file.{Paths, Files}

object Reader {
  implicit final class ARFFToData(val d: Instances) {
    def getAttributes() =
      Range(0, d.numAttributes).map(d.attribute)

    def getDataCases() =
      (0 until d.numInstances).map(d.instance)
        .map(i => Data.Instance(
          (0 until d.numAttributes).map(i.value).toArray, i.weight))
        
    def toData() = {
      def convert(a: Attribute) = {
        val states = (0 until a.numValues).map(a.value)
        new Variable(a.name, new ArrayList(states))
      }

      val attributes = getAttributes()
      val instances = getDataCases()

      new Data(attributes.map(convert), instances)
    }
  }
  
  implicit final class HLCMToData(val d: DataSet) {    
    def getAttributes() =
      d.getVariables.toIndexedSeq

    def getDataCases() =
      d.getData.map(i => Data.Instance(i.getStates.map(_.toDouble).toArray, i.getWeight)).toIndexedSeq
        
    def toData() = {
      def convert(a: Attribute) = {
        val states = (0 until a.numValues).map(a.value)
        new Variable(a.name, new ArrayList(states))
      }

      val attributes = getAttributes()
      val instances = getDataCases()

      new Data(attributes, instances)
    }
  }
  
  val logger = LoggerFactory.getLogger(Reader.getClass)

  def readLTM(modelFile: String) = {
    val model = new LTM()
    new BifParser(new FileInputStream(modelFile), "UTF-8").parse(model)
    model
  }

  def readModel(modelFile: String) = readLTM(modelFile)
  
  def readHLCM(dataFile: String) = HlcmReader.read(dataFile)
  
  /**
   * Native Java HLCM reader
   */
  def readHLCM_native(dataFile: String) = new DataSet(dataFile)

  def readARFF(dataFile: String) = new DataSource(dataFile).getDataSet
  
  def readTuple(dataFile: String) = TupleReader.read(dataFile)
  
  def readLda(dataFile: String, vocabFile: String) = LdaReader.read(dataFile, vocabFile)

  /**
   * Auto detect file format and cast it to scala Data
   * If lda format, please provide vocab file
   */
  def readData(dataFile: String, vocabFile: String = null, format: Option[String] = None):Data = {
    if(format.isDefined){
      format.get.toLowerCase match{
        case "arff" => readARFF(dataFile).toData()
        case "tuple" => readTuple(dataFile)
        case "hlcm" => readHLCM(dataFile)
        case "lda" => readLda(dataFile, vocabFile)
        case _ => throw new Exception("Unknown format")
      }
    }else{
      if(dataFile.endsWith(".arff"))
        readARFF(dataFile).toData()
      else if(dataFile.endsWith(".sparse.txt"))
        readTuple(dataFile)
      else if(dataFile.endsWith(".lda.txt"))
        readLda(dataFile, vocabFile)
      else
        readHLCM(dataFile)
    }
  }
  
  /**
   * Native HLCM reader
   */
  def readLTMAndHLCM_native(modelFile: String, dataFile: String): (LTM, DataSet) = {
    val model = readLTM(modelFile)
    val data = readHLCM_native(dataFile).synchronize(model)
    (model, data)
  }
  
  def readLTMAndHLCM(modelFile: String, dataFile: String): (LTM, Data) = {
    val model = readLTM(modelFile)
    val data = readHLCM(dataFile).synchronize(model)
    (model, data)
  }
  
  def readLTMAndTuple(modelFile: String, dataFile: String): (LTM, Data) = {
    val model = readLTM(modelFile)
    val data = readTuple(dataFile).synchronize(model)
    (model, data)
  }  

  /**
   * Reads a model and a data set from the given files.  The returned data
   * set uses the same variable objects as in the model.  Attribute not found
   * in the model will be discarded.
   */
  def readLTMAndARFF(modelFile: String, dataFile: String): (LTM, Data) = {
    val model = readLTM(modelFile)

    logger.info("Reading ARFF data")
    val arffData = readARFF(dataFile)
    logger.info("Getting attributes")
    val attributes = arffData.getAttributes()
    logger.info("Getting instances")
    val instances = arffData.getDataCases()

    // remove attributes not found in the model
    val nameToVariableMap =
      model.getVariables.toIndexedSeq.map(v => (v.getName, v)).toMap

    val pairs = attributes.zipWithIndex
      .map(p => (nameToVariableMap.get(p._1.name), p._2))
    pairs.filter(_._1.isEmpty).foreach { p =>
      logger.warn("Attribute {} is not found in model.", attributes(p._2).name())
    }
    val (variables, indices) = pairs
      .collect({ case (Some(v), i) => (v, i) })
      .unzip
    val indicesArray = indices.toArray

    val data = new Data(variables, instances.map(_.select(indicesArray)))
    (model, data)
  }
  
  /**
   * Auto detect file format and cast it to scala Data
   */
  def readModelAndData(modelFile: String, dataFile: String): (LTM, Data) = {
    if(dataFile.endsWith(".arff"))
      readLTMAndARFF(modelFile, dataFile)
    else if(dataFile.endsWith(".sparse.txt"))
      readLTMAndTuple(modelFile, dataFile)
    else
      readLTMAndHLCM(modelFile, dataFile)
  }

  //  def replaceVariablesInDataByModel[M <: BayesNet](data: Data, model: M) = {
  //    formDataWithVariablesInModel(data.variables, data.instances, model)
  //  }
  //
  //  /**
  //   * If variable is not found in model, the function will use the default one.
  //   */
  //  def findVariablesInModel[M <: BayesNet](
  //    variableNames: IndexedSeq[String], model: M, default: (String) => Variable) = {
  //    val nameToVariableMap =
  //      model.getVariables.toIndexedSeq.map(v => (v.getName, v)).toMap
  //
  //    variableNames.map(n => nameToVariableMap.getOrElse(n, default(n)))
  //  }
  //
  //  def formDataWithVariablesInModel[M <: BayesNet](
  //    variableNames: IndexedSeq[String], instances: IndexedSeq[Data.Instance],
  //    model: M) = {
  //    def createVariable(n: String) = {
  //      val states = new ArrayList((0 to 1).map(_.toString))
  //      new Variable(n, states)
  //    }
  //    (model, Data(findVariablesInModel(variableNames, model, createVariable), instances))
  //  }
}