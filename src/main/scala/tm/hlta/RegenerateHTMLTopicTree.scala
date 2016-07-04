package tm.hlta

import org.latlab.io.bif.BifParser
import java.io.FileInputStream
import org.latlab.model.LTM
import org.latlab.util.DataSet
import collection.JavaConversions._
import java.io.PrintWriter
import scala.io.Source
import tm.util.Tree
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import tm.util.FileHelpers

object TopicTable {

}

object RegenerateHTMLTopicTree {
  import TopicTree.Topic

  def main(args: Array[String]) {
    if (args.length < 2) {
      printUsage()
    } else {
      val topicsFile = args(0)
      val outputName = args(1)

      val title = if (args.length > 2) args(2) else "Topic Tree"

      val order = readIslands(
        FindTopLevelSiblingClusters.getIslandsFileName(outputName))
      copyAssetFiles(Paths.get("."))
      generateTopicTree(topicsFile, title, outputName, order)
    }
  }

  def printUsage() = {
    //    println("ReorderTopics model_file data_file")
    println("RegenerateHTMLTopicTree topics_file output_name [title]")
    println
    println("e.g. RegenerateHTMLTopicTree TopicsTable.html output \"Topic Tree\"")
    println("It generates the ${output_name}.nodes.js file and uses " +
      "${output_name}.topics.js and ${output_name}.titles.js if they exist.")
  }
  /**
   * Constructs a variable to index mapping for ordering the top level variables
   * by reading the islands file.
   */
  def readIslands(islandsFileName: String) = {
    if (FileHelpers.exists(islandsFileName)) {
      Source.fromFile(islandsFileName).getLines
        .flatMap(_.split(","))
        .zipWithIndex.toMap
    } else {
      println(s"Islands file ${islandsFileName} not found.  " +
        "It uses default ordering of top level topics.")
      Map.empty[String, Int].withDefaultValue(0)
    }
  }

  def generateTopicTree(topicsFile: String, title: String,
    outputName: String, topLevelOrder: Map[String, Int]) = {
    val topLevelTrees = HTMLTopicTable.readTopicTree(topicsFile)
      .sortBy { t => topLevelOrder(t.value.name) }
    //    val parents = topLevelTrees.map(t =>
    //      topLevelTrees.find(_.getChild(t.value).isDefined)
    //        .map(_.value.name).getOrElse("none"))
    //    val content = topLevelTrees.zip(parents).foldLeft("") {
    //      (s, p) => s + treeToHtml(p._1, p._2, 4)
    //    }

    import Implicits.topicToNode

    JSONTreeWriter.writeJSONOutput(topLevelTrees, outputName + ".nodes.js")
    writeHtmlOutput(title, outputName, outputName + ".html")
  }

  object Implicits {
    import JSONTreeWriter.Node

    implicit val topicToNode: (Topic) => Node = (topic: Topic) => {
      val label = f"${topic.percentage}%.2f ${topic.words.mkString(" ")}"
      val data = f"""name: "${topic.name}", level: ${topic.level}, percentage: ${topic.percentage}, mi: ${topic.mi.getOrElse(Double.NaN)}"""
      Node(topic.name, label, data)
    }
  }

  def writeHtmlOutput(title: String, outputName: String, outputFile: String) = {
    val template = Source.fromInputStream(
      this.getClass.getResourceAsStream("/tm/hlta/template.html"))
      .getLines.mkString("\n")

    val writer = new PrintWriter(outputFile)

    def replace(s: String, target: String) = {
      s.replaceAll(s""""${target}"""", s""""${outputName}.${target}"""")
    }

    var content = Seq("nodes.js", "topics.js", "titles.js")
      .foldLeft(template)(replace)
    content = content.replaceAll("<!-- title-placeholder -->", title)

    writer.print(content)
    writer.close
  }

  def copyAssetFiles(basePath: Path) = {
    val baseDir = Option(basePath).getOrElse(Paths.get("."))
    val assetDir = baseDir.resolve("lib")
    val fontsDir = baseDir.resolve("fonts")

    if (!Files.exists(assetDir))
      Files.createDirectories(assetDir)

    if (!Files.exists(fontsDir))
      Files.createDirectories(fontsDir)

    def copyTo(source: String, dir: Path, target: String) = {
      val input = this.getClass.getResourceAsStream(source)
      println(s"Copying from resource ${source} to file ${dir.resolve(target)}")
      Files.copy(input, dir.resolve(target), StandardCopyOption.REPLACE_EXISTING)
    }

    def copy(obj: Object, dir: Path) = obj match {
      case (source: String, target: String) => copyTo(source, dir, target)
      case (source: String) => {
        val index = source.lastIndexOf("/")
        val name = if (index < 0) source else source.substring(index + 1)
        copyTo(source, dir, name)
      }
    }

    Seq(
      ("/tm/hlta/jquery-2.2.3.min.js", "jquery.min.js"),
      "/tm/hlta/jstree.min.js",
      //      "/tm/hlta/jquery.magnific-popup.min.js",
      "/tm/hlta/jquery.tablesorter.min.js",
      "/tm/hlta/jquery.tablesorter.widgets.js",
      "/tm/hlta/magnific-popup.css",
      "/tm/hlta/custom.js",
      "/tm/hlta/custom.css",
      //      "/tm/hlta/tablesorter/blue/asc.gif",
      //      "/tm/hlta/tablesorter/blue/bg.gif",
      //      "/tm/hlta/tablesorter/blue/desc.gif",
      //      ("/tm/hlta/tablesorter/blue/style.css", "tablesorter.css"),
      ("/tm/hlta/tablesorter/themes/theme.bootstrap.css", "tablesorter.css"),
      //      "/tm/hlta/tablesorter/themes/bootstrap-black-unsorted.png",
      //      "/tm/hlta/tablesorter/themes/bootstrap-white-unsorted.png",
      "/tm/hlta/jstree/themes/default/style.min.css",
      "/tm/hlta/jstree/themes/default/32px.png",
      "/tm/hlta/jstree/themes/default/40px.png",
      "/tm/hlta/jstree/themes/default/throbber.gif",
      "/tm/hlta/ie10-viewport-bug-workaround.css",
      "/tm/hlta/ie10-viewport-bug-workaround.js",
      "/tm/hlta/bootstrap.min.css",
      "/tm/hlta/bootstrap.min.js")
      .foreach(p => copy(p, assetDir))

    Seq("/tm/hlta/bootstrap/fonts/glyphicons-halflings-regular.eot",
      "/tm/hlta/bootstrap/fonts/glyphicons-halflings-regular.svg",
      "/tm/hlta/bootstrap/fonts/glyphicons-halflings-regular.ttf",
      "/tm/hlta/bootstrap/fonts/glyphicons-halflings-regular.woff",
      "/tm/hlta/bootstrap/fonts/glyphicons-halflings-regular.woff2")
      .foreach(p => copy(p, fontsDir))
  }

  //  def treeToHtml(tree: Tree[Topic], parent: String, indent: Int): String = {
  //    import tree.value
  //    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
  //    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)
  //
  //    val start = """<li class="jstree-open" id="%s" >""".format(value.name)
  //    val content = f"${value.percentage}%.2f ${value.words.mkString(" ")}"
  //    val end = "</li>"
  //
  //    if (tree.children.isEmpty)
  //      " " * indent + start + content + end + "\n"
  //    else {
  //      val childIndent = indent + 2
  //
  //      " " * indent + start + content + "\n" +
  //        " " * childIndent + "<ul>" + "\n" +
  //        tree.children.map(treeToHtml(_, value.name, childIndent)).reduce(_ + _) +
  //        " " * childIndent + "</ul>" + "\n" +
  //        " " * indent + end + "\n"
  //    }
  //  }

}

object JSONTreeWriter {
  case class Node(id: String, label: String, data: String)

  def writeJSONOutput[T](trees: Seq[Tree[T]], outputFile: String)(implicit convert: (T) => Node) = {
    val writer = new PrintWriter(outputFile)
    writer.print("var nodes = [")
    writer.println(trees.map(treeToJson(_, 0)).mkString(", "))
    writer.println("];")
    writer.close
  }

  def treeToJson[T](tree: Tree[T], indent: Int)(implicit convert: (T) => Node): String = {
    val node = convert(tree.value)
    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)

    val children = tree.children.map(treeToJson(_, indent + 4))

    val json = """{
      |  id: "%s", text: "%s", state: { opened: true, disabled: false, selected: false}, data: { %s }, li_attr: {}, a_attr: {}, children: [%s]
      |}""".format(node.id, node.label, node.data, children.mkString(", "))
      .replaceAll(" +\\|", " " * indent)

    json
  }
}