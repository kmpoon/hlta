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
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.ArrayList
import org.latlab.graph.DirectedNode
import org.latlab.reasoner.CliqueTreePropagation
import org.latlab.model.BeliefNode

trait AssignTopics {
  def logger: Logger
  def name: String
  def suffix: String

  def main(args: Array[String]) {
    if (args.length < 3)
      printUsage()
    else
      run(args(0), args(1), args(2))
  }

  def printUsage() = {
    println(s"${name} model_file data_file outputName")
    println
    println(s"E.g. ${name} model.bif data.arff output")
    println(s"The output file will be ${getFileName("output", "js")} and ${getFileName("output", "arff")}")
  }

  def getFileName(output: String, ext: String) = s"${output}${suffix}.topics.${ext}"

  def run(modelFile: String, dataFile: String, outputName: String): Unit = {
    val topicDataFile = getFileName(outputName, "arff")
    val topicData = if (Files.exists(Paths.get(topicDataFile))) {
      logger.info("Topic data file ({}) exists.  Skipped computing topic data.", topicDataFile)
      Reader.readData(topicDataFile)
    } else {
      val topicData = computeTopicData(modelFile, dataFile)

      logger.info("Saving topic data")
      outputName + "-topics"

      topicData.saveAsArff(outputName + "-topics",
        topicDataFile, new DecimalFormat("#0.##"))
      topicData
    }

    logger.info("Generating topic map")
    val map = generateTopicToDocumentMap(topicData, 0.5)

    logger.info("Saving topic map")
    writeTopicMapJs(map, getFileName(outputName, "js"))
    writeTopicMapJson(map, getFileName(outputName, "json"))

    logger.info("Done")
  }

  def computeTopicData(modelFile: String, dataFile: String): Data = {
    logger.info("reading model and data")
    val (model, data) = Reader.readLTMAndARFFData(modelFile, dataFile)
    val variableNames = data.variables.map(_.getName)

    logger.info("binarizing data")
    val binaryData = data.binary
    model.synchronize(binaryData.variables.toArray)

    logger.info("Computing topic distribution")
    computeTopicData(model, binaryData)
  }

  def computeTopicData(model: LTM,
    binaryData: Data): Data

  /**
   * Generates a list of documents for each topic.
   *
   * Each map value is a sequence of pairs where first element indicates
   * the probability and second element the document index.
   * The sequence is sorted in descending order of probability.
   */
  def generateTopicToDocumentMap(data: Data, threshold: Double) = {
    (0 until data.variables.size).map { v =>
      val documents = data.instances.view.map(_.values(v)) // map to value of v
        .zipWithIndex
        .filter(_._1 >= threshold).force // keep only those values >= threshold
        .sortBy(-_._1) // sort by descending values
      (data.variables(v), documents)
    }.toMap
  }

  def writeTopicMapJs(map: Map[Variable, Seq[(Double, Int)]], outputFile: String) = {
    val writer = new PrintWriter(outputFile)

    writer.println("var topicMap = {")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"[${p._2}%s, ${p._1}%.2f]").mkString(", ")
      s"  ${variable.getName}: [${documents}]"
    }.mkString(",\n"))

    writer.println("};")

    writer.close
  }
  
  def writeTopicMapJson(map: Map[Variable, Seq[(Double, Int)]], outputFile: String) = {
    val writer = new PrintWriter(outputFile)

    writer.println("[")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => f"[${p._2}%s, ${p._1}%.2f]").mkString(",")
      "{\"topic\":\""+variable.getName+"\",\"doc\":["+documents+"]}"
    }.mkString(",\n"))

    writer.println("]")

    writer.close
  }
}

/**
 * Assign broadly defined topics to the documents.
 * It computes the probabilities of the latent variables in a LTM and
 * assign those topics that have a higher probability of state 1
 * to each document.
 */
object AssignBroadTopics extends AssignTopics {
  val logger = LoggerFactory.getLogger(AssignBroadTopics.getClass)
  val name = "AssignBroadTopics"
  val suffix = ""

  def computeTopicData(model: LTM, binaryData: Data): Data = {
    val variables = model.getInternalVars.toIndexedSeq

    // find the probabilities of state 1 for each variable
    val topicProbabilities =
      HLTA.computeProbabilities(model, binaryData, variables)
        .map(p => Data.Instance(p._1.toArray.map(_(1)), p._2))

    Data(variables, topicProbabilities.toIndexedSeq)
  }
}

/**
 * Assign narrowly defined topics to the documents.
 * It computes the probabilities of the latent variables in a LTM and
 * assign those topics that have a higher probability of state 1
 * to each document.
 */
object AssignNarrowTopics extends AssignTopics {
  val logger = LoggerFactory.getLogger(AssignNarrowTopics.getClass)
  val name = "AssignNarrowTopics"
  val suffix = "-ndt"

  private class Extractor(model: LTM, data: Data) extends ExtractNarrowTopics_LCM {
    // Holds topic probabilities (value) for each document for each latent variable (key)
    val topicProbabilities = scala.collection.mutable.Map.empty[String, IndexedSeq[Double]]

    def run(): Data = {
      initialize(model, data.toHLCMData(), Array("", "", "tmp", "no", "no", "7"))
      extractTopics()
      convertProbabilities()
    }
    
    def logCompute(latent: String) = logger.info("Computing probabilities for {}", latent)


    override def extractTopicsByCounting(
      latent: String, observed: ArrayList[Variable]) = {
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
        Data.Instance(columns.map(_.apply(i)), data.instances(i).weight)
      }

      Data(variables, instances)
    }

  }

  def computeTopicData(model: LTM, binaryData: Data): Data = {
    new Extractor(model, binaryData).run()
  }

}