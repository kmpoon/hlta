package tm.hlta

import java.io.PrintWriter
import org.latlab.util.DataSet
import java.nio.file.Files
import java.nio.file.Paths
import collection.JavaConversions._
import tm.util.FileHelpers
import tm.util.Reader
import tm.hlta.HLTA._

object FindTopLevelSiblingClusters {

  def getIslandsFileName(outputName: String) = s"${outputName}-islands.txt"

  def main(args: Array[String]) {
    if (args.length < 3)
      printUsage
    else
      run(args(0), args(1), args(2))
  }

  def printUsage() = {
    println("FindTopLevelSiblingClusters model_file data_file output_name")
    println
    println("e.g. FindTopLevelSiblingClusters model.bif data.txt output")
  }

  def run(modelFile: String, dataFile: String, outputName: String) = {
    val topLevelDataName = s"${outputName}-top"
    val islandsFile = getIslandsFileName(outputName)
    computeTopLevelTopic(modelFile, dataFile, topLevelDataName)
    findAndSaveTopLevelSiblings(topLevelDataName + ".txt", islandsFile)

  }

  def computeTopLevelTopic(modelFile: String, dataFile: String, outputName: String) = {
    val hlcmDataFile = outputName + ".txt"
    val arffDataFile = outputName + ".arff"

    if (FileHelpers.exists(hlcmDataFile) && FileHelpers.exists(arffDataFile)) {
      println(s"Both data files (${hlcmDataFile} and ${arffDataFile}) exist. " +
        "Skipped computing top level topic assignments.")
    } else {
      val (model, data) = Reader.readLTMAndHLCM_native(modelFile, dataFile)

      println(data.getVariables.length)
      println(data.getData.size())

      val top = model.getTopLevelVariables

      //    val topLevelData = PEMTools.HardAssignment(data, model, top.toArray)
      val topLevelData = HLTA.hardAssignment(data, model, top.toArray)
      topLevelData.save(outputName + ".txt")
      topLevelData.saveAsArff(outputName + ".arff", false)

      println(top.map(_.getName).mkString(", "))
    }
  }

  /**
   * Find sibling clusters of top level topics.  The sibling clusters are used
   * to reorder the top level topics.
   */
  def findAndSaveTopLevelSiblings(dataFile: String, outputFile: String) = {
    if (FileHelpers.exists(outputFile)) {
      println(s"Islands file (${outputFile}) exists. " +
        "Skipped finding top-level silbing clusters.")
    } else {
      val data = new DataSet(dataFile)
      val finder = new IslandFinder
      val ltms = finder.find(data)
      val leaves = ltms.map(_.getLeafVars)
      val output = leaves.map(_.map(_.getName).mkString(",")).mkString("\n")

      println(output)

      val writer = new PrintWriter(outputFile)
      writer.println(output)
      writer.close
    }
  }
}