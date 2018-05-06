package tm.util

import org.latlab.util.Variable
import org.latlab.util.DataSet
import org.latlab.learner.SparseDataSet
import org.mymedialite.data.EntityMapping
import org.mymedialite.data.PosOnlyFeedback
import org.mymedialite.datatype.SparseBooleanMatrix
import scala.collection.JavaConversions._
import java.text.DecimalFormat
import java.util.ArrayList
import org.latlab.model.LTM
import org.slf4j.LoggerFactory
import scala.util.Random
import tm.text.Dictionary
import tm.text.NGram

object Data{
  type TokenCounts = Map[NGram, Int]
  def fromDictionaryAndTokenCounts(dictionary: Dictionary, tokenCountsSeq: Seq[TokenCounts], isBinary: Boolean = false, name: String = "data"): Data = { 
    
    def _newVariable(name: String) = {
      val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")         
      new Variable(name, b)
    }
    
    def _toBow(indices: Map[NGram, Int], counts: TokenCounts): Array[Double] = {
      val values = Array.fill(indices.size)(0.0)
      counts.foreach { wc =>
        indices.get(wc._1).foreach { i => values(i) = wc._2 }
      }
      values
    }
    
    val tokenIndices = dictionary.map
    val variables = dictionary.info.map{wordInfo => _newVariable(wordInfo.token.identifier)}
    val instances = tokenCountsSeq.zipWithIndex.map{ case(tokenCounts, index) => 
      val values = _toBow(tokenIndices, tokenCounts)
      //Each instance has a unique name
      //Such that even data is cut, instance name remain the same as tokenCountsSeq's order
      new Data.Instance(values, 1.0, name = index.toString())
    }
    new Data(variables, instances.toIndexedSeq, isBinary, name)
  }
  
  private def binarize(value: Double): Double = if (value > 0) 1.0 else 0.0
  //The idea of count data has been abandoned
  //private def binarize(variable: Variable): Variable = new Variable(variable.getName, new ArrayList(Seq("s0", "s1")))
  
  case class Instance(val values: Array[Double], val weight: Double, val name: String = "") {    
    def copy(values: Array[Double] = values, weight: Double = weight, name: String = name) = new Instance(values, weight, name)
    def select(indices: Array[Int]) = copy(values = indices.map(values.apply))
    def apply(variablePos: Int) : Double = values.apply(variablePos)
  }
    
  implicit class DataSetMethod(dataSet: DataSet){
    /** Returns a data set that is the projection of this data set on the
  	 * specified list of variables.
  	 * 
  	 * Added by Leung Chun Fai
  	 * 
  	 * @param instancesId
  	 *            list of instances onto which this data set is to be project.
  	 * @return a subset of the original data set on the specified list of variables(enforced order).
  	 */
  	def subset(instancesId: Seq[Int]) = {
  		val newDataSet = new DataSet(dataSet.getVariables)
  		instancesId.filter{ instanceId => instanceId < dataSet.getData.size }.foreach{ instanceId => 
  		  val dataCase = dataSet.getData.get(instanceId)
  		  newDataSet.addDataCase(dataCase.getStates, dataCase.getWeight)
  		}  
  		newDataSet
  	}
  	
  	def randomSubset(ratio: Double) = {
  	  val indices = Random.shuffle(List.range(1, dataSet.getData.size)).take((dataSet.getData.size*ratio).toInt)
  	  subset(indices)
  	}
  }
  
  implicit class SparseDataSetMethod(sparseDataSet: SparseDataSet){
  	def randomSlice(split: Int) = sparseDataSet.GetNextPartition(split, Random.nextInt(split))
  }
}

class Data(val variables: IndexedSeq[Variable], val instances: IndexedSeq[Data.Instance], val isBinary: Boolean = false, val name : String = "data") {

  //To avoid collision with reserved words in .bif file
  variables.foreach(variable => if(BifProperties.ReservedWords.exists(_.equals(variable.getName))) variable.setName(variable.getName+"_"))
  
  val logger = LoggerFactory.getLogger(Data.getClass)
  
  def copy(variables: IndexedSeq[Variable] = variables, instances: IndexedSeq[Data.Instance] = instances, isBinary: Boolean = isBinary) = new Data(variables, instances, isBinary)
  
  def tf(v: String): Double = tf(Seq[String](v))
  
  def tf(vs: Seq[String]): Double = {
    val pos = variables.zipWithIndex.filter {case (variable, index) => vs.contains(variable.getName) }.map(_._2)
    if(pos.size!=vs.size)
      0.0
    else
      instances.map(instance => pos.map{ p => instance.apply(p) }.min * instance.weight).sum
  }
  
  def df(v: String): Double = df(Seq[String](v))
  
  def df(vs: Seq[String]): Double = {
    val pos = variables.zipWithIndex.filter {case (variable, index) => vs.contains(variable.getName) }.map(_._2)
    if(pos.size!=vs.size)
      0.0
    else
      instances.filter(instance => pos.forall{ p => instance.apply(p)>=1.0 }).map(_.weight).sum
  }
  
  def size() = instances.size
  
  def dimension() = variables.size
  
  def binary(): Data = {
    if(isBinary)
      this
    else
      copy(
        variables = variables,//.map(Data.binarize), //Variable cardinality will always be 2
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

  /**
   * Convert to DataSet (HLCM format)
   * Note that HLCM format does not preserve ordering
   */
  def toHlcmDataSet(): DataSet = {
    val data = new DataSet(variables.toArray)

    // map from new index to old index
    val indices = data.getVariables.map(variables.indexOf(_)).toArray

    instances.foreach { i =>
      val values = indices.map(i.values.apply).map(_.toInt)
      data.addDataCase(values, i.weight)
    }
    data.setName(name)

    data
  }

  /**
   * Convert to SparseDataSet (the Tuple format)
   * Note that this format is binary
   * 
   * Implementation refer to org.mymedialite.io.ItemData.Read
   */
  def toTupleSparseDataSet(): SparseDataSet = {
    val feedback = new PosOnlyFeedback[SparseBooleanMatrix](classOf[SparseBooleanMatrix]);
    val userMapping = new EntityMapping();
    val itemMapping = new EntityMapping();
		instances.zipWithIndex.foreach{ case (instance, docSeq) =>
      instance.values.zipWithIndex.filter { case (x, wordId) => x>=0.5 }.foreach{ case (x, wordId) => 
        val docId = if(instance.name.length()>0) instance.name else (docSeq+1).toString()
        val word = variables(wordId).getName
        val user_id = userMapping.toInternalID(docId)
		    val item_id = itemMapping.toInternalID(word)
        feedback.add(user_id, item_id);
      }
    }
		new SparseDataSet(variables.toArray[Variable], userMapping, itemMapping, feedback)		
  }

  def saveAsArff(filename: String, df: DecimalFormat = new DecimalFormat("#0.##")) = {
    ArffWriter.write(name, filename,
      if(isBinary) ArffWriter.AttributeType.binary else ArffWriter.AttributeType.numeric,
      variables.map(_.getName), instances, df.format)
  }
  
  /**
   * Save as .hlcm
   * 
   * Note that,
   * 1. this format is binary
   * 2. this format does not preserve instance order
   */
  def saveAsHlcm(filename: String) = {
    toHlcmDataSet().save(filename)
  }
  
  /**
   * Save as .sparse.txt, see org.latlab.learner.SparseDataSet
   * 
   * Note that, this format is binary
   */
  def saveAsTuple(filename: String) = {
    TupleWriter.write(filename, variables.map(_.getName), instances)
  }
  
  /**
   * Save as .lda and .vocab
   * 
   * Note that, this format only takes integer
   */
  def saveAsLda(dataFileName: String, vocabFileName: String) = {
    LdaWriter.writeData(dataFileName, instances)
    LdaWriter.writeVocab(vocabFileName, variables.map(_.getName))
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
  
  def split(ratio: Double) = {
    val (set1, set2) = instances.splitAt((this.instances.size*ratio).toInt)
    (new Data(variables, set1, isBinary, name), new Data(variables, set2, isBinary, name))
  }
  
  def randomSubset(ratio: Double) = {
    val instances = Random.shuffle(this.instances).take((this.instances.size*ratio).toInt)
    new Data(variables, instances, isBinary, name)
  }
  
  def randomSplit(ratio: Double) = {
    val (set1, set2) = Random.shuffle(this.instances).splitAt((this.instances.size*ratio).toInt)
    (new Data(variables, set1, isBinary, name), new Data(variables, set2, isBinary, name))
  }
  
  def select[A, B](varList: IndexedSeq[A], docList: Seq[B]) = subset(docList).project(varList)
}

