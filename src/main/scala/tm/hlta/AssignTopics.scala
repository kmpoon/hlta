package tm.hlta

import collection.JavaConversions._
import org.latlab.util.Variable
import tm.util.ArffWriter
import java.text.DecimalFormat
import tm.util.Data
import org.latlab.model.LTM
import java.nio.file.Files
import java.nio.file.Paths
import java.io.PrintWriter
import scala.io.Source
import org.json4s._
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.ArrayList
import org.latlab.graph.DirectedNode
import org.latlab.reasoner.CliqueTreePropagation
import org.latlab.model.BeliefNode
import org.apache.commons.lang.StringEscapeUtils

import org.rogach.scallop._
import tm.util.Arguments
import tm.util.Reader
import tm.hlta.HLTA._

object Doc2VecAssignment {
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: Doc2VecAssignment [OPTION]... model data output_name
             |E.g. Doc2VecAssignment model.bif data.arff output
             |The output file will be """+getFileName("output", "js")+""" and """+getFileName("output", "arff")+"""
             |"The number of decimal places is used in the ARFF file only.""")
             
    val model = trailArg[String]()
    val data = trailArg[String](descr = "**Special** Only .arff or .sparse.txt are allowed")
    val outputName = trailArg[String]()
    
    val decimalPlaces = opt[Int](descr="Significant figure", default = Some(2))
    val layer = opt[List[Int]](descr = "Layer number, i.e. 2 3 4", default = None)
    val confidence = opt[Double](descr = "Only document with P(topic|document)>c will be listed in the list, default 0.5", default = Some(0.5))
    val broad = opt[Boolean](descr = "Use broad topic definition, speed up the process but more document will be categorized into the topic")

    verify
    checkDefaultOpts()
  }
  
  val logger = LoggerFactory.getLogger(Doc2VecAssignment.getClass)

  def main(args: Array[String]) {
     val conf = new Conf(args)

    if(conf.data().endsWith(".hlcm"))
      throw new Exception("Invalid data format")
     
     run(conf.model(), conf.data(), conf.outputName(), conf.decimalPlaces(), conf.layer.toOption, conf.confidence(), conf.broad())
  }

  def run(modelFile: String, dataFile: String, outputName: String, decimalPlaces: Int, layer: Option[List[Int]], threshold : Double, broad : Boolean): Unit = {
    val topicDataFile = getFileName(outputName, "arff")
    val topicData = if (Files.exists(Paths.get(topicDataFile))) {
      logger.info("Topic data file ({}) exists.  Skipped computing topic data.", topicDataFile)
      val topicData = Reader.readData(topicDataFile)
      if(layer.isDefined){
        val variableNameLevels = Reader.readModel(modelFile).getVariableNameLevels
        val topicToBeKept = topicData.variables.filter { variable => 
          val level = variableNameLevels(variable.getName)
          layer.get.contains(level)
        }
        topicData.project(topicToBeKept)
      }else
        topicData
    } else {
      logger.info("reading model and data")
      val (model, data) = Reader.readModelAndData(modelFile, dataFile)
      val variableNames = data.variables.map(_.getName)
  
      //TODO: check if binary + sync still works
      logger.info("binarizing data")
      val binaryData = data.binary
      model.synchronize(binaryData.variables.toArray)
  
      logger.info("Computing topic distribution")
      val topicData = if(broad)
        computeBroadTopicData(model, binaryData, layer)
      else
        computeNarrowTopicData(model, binaryData, layer)

      logger.info("Saving topic data")
      outputName + "-topics"

      val df = new DecimalFormat("#0." + "#" * decimalPlaces)
      topicData.saveAsArff(outputName, df)
      topicData
    }

    logger.info("Generating document catalog")
    val catalog = topicData.toCatalog(threshold = threshold)

    logger.info("Saving topic map")
    catalog.saveAsJs(getFileName(outputName, "js"), decimalPlaces)
    catalog.saveAsJson(getFileName(outputName, "json"), decimalPlaces)

    logger.info("Done")
  }
    
  def getFileName(output: String, ext: String) = s"${output}.topics.${ext}"
  
  /**
   * Assign broadly defined topics to the documents.
   * It computes the probabilities of the latent variables in a LTM and
   * assign those topics that have a higher probability of state 1
   * to each document.
   */
  def computeBroadTopicData(model: LTM, binaryData: Data, layer: Option[List[Int]]): Data = { 
    //get the list of variables to be computed
    val variables = if(layer.isEmpty)
      model.getInternalVars.toSeq
    else{
      val variablesByLevel = model.getLevelVariables
      //variablesByLevel: {0->word, 1->topic, ... , topic_height-1 ->root}
      //but layer could be negative or 0, where root=0, root's child=-1, etc.
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      _layer.map(variablesByLevel.get(_)).flatten.flatten.toSeq
    }

    // find the probabilities of state 1 for each variable
    val topicProbabilities =
      HLTA.computeProbabilities(model, binaryData, variables).map(p => Data.Instance(p._1.toArray.map(_(1)), p._2, p._3))

    new Data(variables.toIndexedSeq, topicProbabilities.toIndexedSeq)
  }
  
  def computeNarrowTopicData(model: LTM, binaryData: Data, layer: Option[List[Int]]): Data = new NarrowTopicExtractor(model, binaryData, layer).apply()
    
  implicit final class toCatalog(data: Data){
    /**
     * Generates a list of documents for each topic.
     *
     * Each map value is a sequence of pairs where first element indicates
     * the probability and second element the document index.
     * The sequence is sorted in descending order of probability.
   	 */
    def toCatalog(threshold: Double): DocumentCatalog = {
      val map = (0 until data.variables.size).map { v =>
        val documents = data.instances.view.zipWithIndex.map{case (d, i) =>
          // map to value of v
          if(d.name.isEmpty)
            (i.toString(), d.values(v))//use index as document name
          else
            (d.name, d.values(v))//use the provided instance name
          } 
          .filter(_._2 >= threshold).force // keep only those values >= threshold
          .sortBy(-_._2) // sort by descending values
        (data.variables(v), documents)
      }.toMap
      DocumentCatalog(map)
    }
  }
  
}

/**
 * Assign narrowly defined topics to the documents.
 * It computes the probabilities of the latent variables in a LTM and
 * assign those topics that have a higher probability of state 1
 * to each document.
 */
private class NarrowTopicExtractor(model: LTM, data: Data, layer: Option[List[Int]]) extends ExtractNarrowTopics_LCM {
  // Holds topic probabilities (value) for each document for each latent variable (key)
  val topicProbabilities = scala.collection.mutable.Map.empty[String, IndexedSeq[Double]]
  val varLevels = model.getVariableNameLevels
  val _layer = if(layer.isDefined)  layer.get.map{l => if(l<=0) l+model.getHeight-1 else l} else null

  def apply(): Data = {
    initialize(model, data.toHlcmDataSet, Array("", "", "tmp", "no", "no", "7"))
    extractTopics()
    convertProbabilities()
  }

  def logCompute(latent: String) = logger.info("Computing probabilities for {}", latent)
  
  override def extractTopicsByCounting(latent: String, observed: ArrayList[Variable]){
    if(_layer != null && !_layer.contains(varLevels(latent)))
      return
      
    logCompute(latent)
    
    val indices = observed.map(data.variables.indexOf)
    val probabilities = data.instances.map { i =>
      if (indices.map(i.values.apply).find(_ > 0.0).isDefined)
        1.0
      else
        0.0
    }
    topicProbabilities += (latent -> probabilities)
  }

  override def extractTopicsBySubtree(
    latent: String, setVars: ArrayList[Variable],
    setNode: java.util.Set[DirectedNode], subtree: LTM) {
    if(_layer != null && !_layer.contains(varLevels(latent)))
      return
    
    logCompute(latent)

    extractTopicsBySubtree1(latent, setNode, subtree);
    val subtree1 = extractTopicsBySubtree2(latent, setNode, subtree);

    // find only observed variables
    val (observed, indices) = setNode.map { n =>
      val v = n.asInstanceOf[BeliefNode].getVariable
      val index = data.variables.indexOf(v)
      if (index >= 0) Some(v, index)
      else None
    }.collect(_ match {
      case Some(p) => p
    }).toArray.unzip

    def getObservedStates(instance: Data.Instance) =
      indices.map(instance.values.apply).map(v => if (v > 0) 1 else 0)

    val latentVariable = model.getNodeByName(latent).getVariable

    // check 
    val test = observed.map(subtree1.getNode)
    assert(test.forall(_ != null))

    val ctp = new CliqueTreePropagation(subtree1);
    val probabilities = data.instances.map { i =>
      ctp.setEvidence(observed, getObservedStates(i))
      ctp.propagate();
      ctp.computeBelief(latentVariable).getCells()(1)
    }

    topicProbabilities += (latent -> probabilities)
  }

  def convertProbabilities() = {
    val (names, columns) = topicProbabilities.toArray.unzip
    val variables = names.map(model.getNodeByName).map(_.getVariable)

    val instances = data.instances.indices.map { i =>
      Data.Instance(columns.map(_.apply(i)), data.instances(i).weight, name = data.instances(i).name)
    }

    new Data(variables, instances)
  }

}


object DocumentCatalog{
  case class Entry(topic: String, doc: List[List[Any]])
   def readJson(fileName: String) = {
    import org.json4s.native.JsonMethods._
    implicit val formats = DefaultFormats
    val jsonString = Source.fromFile(fileName).mkString
    val entries = parse(jsonString).extract[List[Entry]]
    val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
    val map = entries.map { e => 
      (new Variable(e.topic, b) -> e.doc.map { x => (x.get(0).asInstanceOf[String], x.get(1).asInstanceOf[Double]) })
    }.toMap
    DocumentCatalog(map)
  }
}

case class DocumentCatalog(map: Map[Variable, Seq[(String, Double)]]){
  
  def apply(variable: String): Seq[(String, Double)] = map.find(v=>v._1.getName.equals(variable)).get._2
  
  def apply(variable: Variable): Seq[(String, Double)] = map.get(variable).get
  
  def apply(variable: String, docName: String): Double = apply(variable).find{case (d, p) => d.equals(docName)}.get._2
  
  def apply(variable: Variable, docName: String): Double = apply(variable).find{case (d, p) => d.equals(docName)}.get._2
  
  def saveAsJs(outputFile: String, decimalPlaces: Int = 2, jsVarName: String = "topicMap"){
    
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeJavaScript(str)
    }
    
    val writer = new PrintWriter(outputFile)

    writer.println("var "+jsVarName+" = {")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"""["${p._1.escape}%s", ${p._2}%.2f]""").mkString(", ")
      s"  ${variable.getName.escape}: [${documents}]"
    }.mkString(",\n"))

    writer.println("};")

    writer.close
  }

  def saveAsJson(outputFile: String, decimalPlaces: Int = 2){
    val writer = new PrintWriter(outputFile)

    writer.println("[")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"""["${p._1}%s", ${p._2}%.2f]""").mkString(",")
      "{\"topic\":\"" + variable.getName + "\",\"doc\":[" + documents + "]}"
    }.mkString(",\n"))

    writer.println("]")

    writer.close
  }
}
