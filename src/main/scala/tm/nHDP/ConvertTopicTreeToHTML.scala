package tm.nHDP

import scala.io.Source
import java.nio.file.Paths
import tm.util.Tree
import java.io.PrintWriter

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
    import JsonTreeWriter.Node

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

    tm.hlta.BuildWebsite.copyAssetFiles(Paths.get("."))

    val topicTrees = TopicTree.read(
      Source.fromFile(topicFile).getLines.toList)

    println(topicTrees.size)

    JsonTreeWriter.writeJsOutput(topicTrees, outputName + ".nodes.js")
    tm.hlta.BuildWebsite.writeHtmlOutput(title, outputName, outputName + ".html")

  }
}

//Copied from tm.hlta.RegenerateHTMLTopicTree
object JsonTreeWriter {
  case class Node(id: String, label: String, data: String)

  def writeJsOutput[T](trees: Seq[Tree[T]], outputFile: String)(implicit convert: (T) => Node) = {
    val writer = new PrintWriter(outputFile)
    writer.print("var nodes = [")
    writer.println(trees.map(treeToJs(_, 0)).mkString(", "))
    writer.println("];")
    writer.close
  }

  def treeToJs[T](tree: Tree[T], indent: Int)(implicit convert: (T) => Node): String = {
    val node = convert(tree.value)
    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)

    val children = tree.children.map(treeToJs(_, indent + 4))

    val js = """{
      |  id: "%s", text: "%s", state: { opened: true, disabled: false, selected: false}, data: { %s }, li_attr: {}, a_attr: {}, children: [%s]
      |}""".format(node.id, node.label, node.data, children.mkString(", "))
      .replaceAll(" +\\|", " " * indent)

    js
  }
  
  def writeJsonOutput[T](trees: Seq[Tree[T]], outputFile: String)(implicit convert: (T) => Node) = {
    val writer = new PrintWriter(outputFile)
    writer.print("[")
    writer.println(trees.map(treeToJson(_, 0)).mkString(", "))
    writer.println("]")
    writer.close
  }

  def treeToJson[T](tree: Tree[T], indent: Int)(implicit convert: (T) => Node): String = {
    val node = convert(tree.value)
    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)

    val children = tree.children.map(treeToJson(_, indent + 4))

    val json = """{
      |  "id": "%s", "text": "%s", "data": {%s}, "children": [%s]
      |}""".format(node.id, node.label, node.data, children.mkString(", "))
      .replaceAll(" +\\|", " " * indent)

    json
  }
}