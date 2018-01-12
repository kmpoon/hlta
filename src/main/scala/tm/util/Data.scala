package tm.util

import org.latlab.util.Variable
import org.latlab.util.DataSet
import scala.collection.JavaConversions._
import scala.collection.LinearSeq
import java.text.DecimalFormat
import java.util.ArrayList
import org.latlab.model.LTM
import org.slf4j.LoggerFactory

object Data{
  def apply(variables: IndexedSeq[Variable], instances: IndexedSeq[Data.Instance], isBinary: Boolean = false) = new Data(variables, instances, isBinary)
  
  private def binarize(value: Double): Double = if (value > 0) 1.0 else 0.0
  private def binarize(variable: Variable): Variable = new Variable(variable.getName, new ArrayList(Seq("s0", "s1")))
  
  object Instance{
    def apply(values: Array[Double], weight: Double, name: String = "") = new Instance(values, weight, name)
  }
  
  class Instance(val values: Array[Double], val weight: Double, val name: String = "") {    
    def copy(values: Array[Double] = values, weight: Double = weight, name: String = name) = new Instance(values, weight, name)
    def select(indices: Array[Int]) = copy(values = indices.map(values.apply))
    def apply(variablePos: Int) : Double = values.apply(variablePos)
  }
  
	/** Returns a data set that is the projection of this data set on the
	 * specified list of variables.
	 * 
	 * Added by Leung Chun Fai
	 * 
	 * @param instancesId
	 *            list of instances onto which this data set is to be project.
	 * @return a subset of the original data set on the specified list of variables(enforced order).
	 */  
  implicit class DataSetMethod(dataSet: DataSet){
  	def subset(instancesId: Seq[Int]) = {
  		val newDataSet = new DataSet(dataSet.getVariables)
  		instancesId.filter{ instanceId => instanceId < dataSet.getData.size }.foreach{ instanceId => 
  		  val dataCase = dataSet.getData.get(instanceId)
  		  newDataSet.addDataCase(dataCase.getStates, dataCase.getWeight)
  		}  
  		newDataSet
  	}
  }
}

class Data(val variables: IndexedSeq[Variable], val instances: IndexedSeq[Data.Instance], val isBinary: Boolean = false) {
  
  val logger = LoggerFactory.getLogger(Data.getClass)
  
  def copy(variables: IndexedSeq[Variable] = variables, instances: IndexedSeq[Data.Instance] = instances, isBinary: Boolean = isBinary) = new Data(variables, instances, isBinary)
  
  def apply(i: Int) : Data.Instance = instances.apply(i)
  
  def apply(i: String) : Data.Instance = instances.find { instance => instance.name==Some(i) }.get
  
  def apply(i: Int, v: String): Double = {
    val pos = variables.zipWithIndex.find {case (variable, index) => variable.getName.equals(v) }
    if(pos.isEmpty)
      0.0
    else
      apply(i).apply(pos.get._2)
  }
  
  def apply(i: String, v: String): Double = {
    val pos = variables.zipWithIndex.find {case (variable, index) => variable.getName.equals(v) }
    if(pos.isEmpty)
      0.0
    else
      apply(i).apply(pos.get._2)
  }
  
  def apply(i: Int, j: Int): Double = {
    if(j>=variables.size)
      0.0
    else
      instances.apply(i).apply(j)
  }
  
  def tf(v: String): Double = tf(Seq[String](v))
  
  def tf(vs: Seq[String]): Double = {
    val pos = variables.zipWithIndex.filter {case (variable, index) => vs.contains(variable.getName) }.map(_._2)
    if(pos.size!=vs.size)
      0.0
    else
      instances.map(instance => pos.map{ p => instance.apply(p) }.min).sum
  }
  
  def df(v: String): Double = df(Seq[String](v))
  
  def df(vs: Seq[String]): Double = {
    val pos = variables.zipWithIndex.filter {case (variable, index) => vs.contains(variable.getName) }.map(_._2)
    if(pos.size!=vs.size)
      0.0
    else
      instances.filter(instance => pos.forall{ p => instance.apply(p)>=1.0 }).size
  }
  
  def size() = instances.size
  
  def binary(): Data = {
    if(isBinary)
      this
    else
      copy(
        variables = variables.map(Data.binarize),
        instances = instances.map(i =>
          i.copy(values = i.values.map(Data.binarize))),
        isBinary = true)
  }
  
  /**
   * Returns a NEW dataset that is synchronized with the model
   */
  def synchronize(model: LTM) = {
    // remove attributes not found in the model
    val nameToVariableMap = model.getVariables.toIndexedSeq.map(v => (v.getName, v)).toMap

    val pairs = this.variables.zipWithIndex.map(v => (nameToVariableMap.get(v._1.getName), v._2))
    pairs.filter(_._1.isEmpty).foreach { p =>
      logger.warn("Attribute {} is not found in model.", this.variables.get(p._2).getName)
    }
    val (variables, indices) = pairs
      .collect({ case (Some(v), i) => (v, i) })
      .unzip
    val indicesArray = indices.toArray

    new Data(variables, instances.map(_.select(indicesArray)), isBinary)
  }

  def toHLCMDataSet(): DataSet = {
    val data = new DataSet(variables.toArray)

    // map from new index to old index
    val indices = data.getVariables.map(variables.indexOf(_)).toArray

    instances.foreach { i =>
      val values = indices.map(i.values.apply).map(_.toInt)
      data.addDataCase(values, i.weight)
    }

    data
  }

  def saveAsArff(name: String, filename: String,
    df: DecimalFormat = new DecimalFormat("#0.##")) = {
    ArffWriter.write(name, filename,
      ArffWriter.AttributeType.numeric,
      variables.map(_.getName), instances, df.format)
  }
  
  def saveAsHlcm(filename: String) = toHLCMDataSet().save(filename)
  
  /**
   * Save as SparseDataSet, see org.latlab.learner.SparseDataSet
   */
  def saveAsTuple(filename: String) = {
    TupleWriter.write(filename, variables.map(_.getName), instances)
  }
  
  def project[A](vars: IndexedSeq[A]) = vars.head match {
    case v: Variable => projectV(vars.asInstanceOf[IndexedSeq[Variable]])
    case s: String => projectS(vars.asInstanceOf[IndexedSeq[String]])
  }

  private def projectV(vars: IndexedSeq[Variable]) = {
    val indices = vars.map(variables.indexOf(_)).toArray
    new Data(vars, instances.map(_.select(indices)), isBinary)
  }
  
  private def projectS(varNames: IndexedSeq[String]) = {
    val vars = varNames.map(name => variables.find { v => v.getName.equals(name) }.get)
    val indices = vars.map(variables.indexOf(_)).toArray
    new Data(vars, instances.map(_.select(indices)), isBinary)
  }
  
  private def subsetI(docList: Seq[Int]) = {
    new Data(variables, docList.map(index=>instances.get(index)).toIndexedSeq, isBinary)
  }
  
  private def subsetS(docList: Seq[String]) = {
    new Data(variables, instances.filter(instance=>docList.contains(instance.name)).toIndexedSeq, isBinary)
  }
  
  def subset[A](docList: Seq[A]) = docList.head match {
    case i: Int => subsetI(docList.asInstanceOf[Seq[Int]])
    case s: String => subsetS(docList.asInstanceOf[Seq[String]])
  }
  
  def select[A, B](varList: IndexedSeq[A], docList: Seq[B]) = subset(docList).project(varList)
}

