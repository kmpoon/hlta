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

object Reader {
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

  def readARFFData(dataFile: String) = new DataSource(dataFile).getDataSet

  def getAttributes(instances: Instances) =
    Range(0, instances.numAttributes).map(instances.attribute)

  def getDataCases(instances: Instances) =
    (0 until instances.numInstances).map(instances.instance)
      .map(i => Data.Instance(
        (0 until instances.numAttributes).map(i.value).toArray, i.weight))

  /**
   * Reads a model and a data set from the given files.  The returned data
   * set uses the same variable objects as in the model.
   */
  def readLTMAndARFFData(modelFile: String, dataFile: String) = {
    val model = readLTM(modelFile)

    println("Reading ARFF data")
    val arffData = readARFFData(dataFile)
    println("Getting attributes")
    val attributes = getAttributes(arffData)
    println("Getting instances")
    val instances = getDataCases(arffData)

    // use the variables in the model
    val nameToVariableMap =
      model.getVariables.toSeq.map(v => (v.getName, v)).toMap

    val variables = attributes.map(_.name).map(nameToVariableMap)

    (model, Data(variables, instances))
  }
}