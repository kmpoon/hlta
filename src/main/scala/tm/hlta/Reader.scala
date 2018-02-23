package tm.hlta

import java.io.FileInputStream

import org.latlab.io.bif.BifParser
import org.latlab.model.LTM
import org.latlab.util.DataSet

import weka.core.converters.ConverterUtils.DataSource
import weka.core.Instances
import org.latlab.util.Variable

import collection.JavaConversions._
import tm.util.Data
import org.latlab.model.BayesNet
import weka.core.Attribute
import java.util.ArrayList
import scala.collection.LinearSeq
import org.slf4j.LoggerFactory
import java.io.InputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream

object Reader {
  implicit final class ARFFToData(val d: Instances) {
    def toData() = {
      def convert(a: Attribute) = {
        val states = (0 until a.numValues).map(a.value)
        new Variable(a.name, new ArrayList(states))
      }

      val attributes = getAttributes(d)
      val instances = getDataCases(d)

      Data(attributes.map(convert), instances)
    }
  }

  val logger = LoggerFactory.getLogger(Reader.getClass)

  def readLTMAndData(modelFile: String, dataFile: String) = {
    val model = readLTM(modelFile)

    val data = readHLCMData(dataFile).synchronize(model)
    (model, data)
  }

  def readLTM(modelFile: String) = {
    val model = new LTM()
    new BifParser(new FileInputStream(modelFile), "UTF-8").parse(model)
    model
  }

  def readHLCMData(dataFile: String) = new DataSet(dataFile)

  def readARFFData(dataFile: String): Instances = {
    val input = if (dataFile.endsWith(".arff"))
      new FileInputStream(dataFile)
    else
      new CompressorStreamFactory()
        .createCompressorInputStream(new BufferedInputStream(new FileInputStream(dataFile)))
    readARFFData(input)
  }
  def readARFFData(dataFile: InputStream): Instances = new DataSource(dataFile).getDataSet

  def readData(dataFile: String) = readARFFData(dataFile).toData

  def getAttributes(instances: Instances) =
    Range(0, instances.numAttributes).map(instances.attribute)

  def getDataCases(instances: Instances) =
    (0 until instances.numInstances).map(instances.instance)
      .map(i => Data.Instance(
        (0 until instances.numAttributes).map(i.value).toArray, i.weight))

  /**
   * Reads a model and a data set from the given files.  The returned data
   * set uses the same variable objects as in the model.  Attribute not found
   * in the model will be discarded.
   */
  def readLTMAndARFFData(modelFile: String, dataFile: String): (LTM, Data) = {
    val model = readLTM(modelFile)

    logger.info("Reading ARFF data")
    val arffData = readARFFData(dataFile)
    logger.info("Getting attributes")
    val attributes = getAttributes(arffData)
    logger.info("Getting instances")
    val instances = getDataCases(arffData)

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

    val data = Data(variables, instances.map(_.select(indicesArray)))
    (model, data)
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