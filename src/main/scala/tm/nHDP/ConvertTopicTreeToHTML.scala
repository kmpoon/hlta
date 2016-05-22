package tm.nHDP

import scala.io.Source
import java.nio.file.Paths
import tm.hlta.JSONTreeWriter

object ConvertTopicTreeToHTML {
  def main(args: Array[String]) = {
    if (args.length < 3)
      printUsage()
    else
      run(args(0), args(1), args(2))
  }

  def printUsage() = {
    println("ConvertnHDPTopicTreeToJSON nHDP_topic_file title output_name")
    println
    println("e.g. ConvertnHDPTopicTreeToJSON topic.txt title output")
  }

  object Implicits {
    import JSONTreeWriter.Node

    implicit val topicToNode: (Topic) => Node = (topic: Topic) => {
      val name = "%04d".format(topic.index)
      val label = topic.words.mkString(" ")
      val data = s"""name: "${name}", level: ${topic.level}"""
      Node(name, label, data)
    }
  }

  def run(topicFile: String, title: String, outputName: String) = {
    import Implicits.topicToNode
    import tm.hlta.RegenerateHTMLTopicTree._

    copyAssetFiles(Paths.get("."))

    val topicTrees = TopicTree.read(
      Source.fromFile(topicFile).getLines.toList)

    println(topicTrees.size)

    JSONTreeWriter.writeJSONOutput(topicTrees, outputName + ".nodes.js")
    writeHtmlOutput(title, outputName, outputName + ".html")

  }
}