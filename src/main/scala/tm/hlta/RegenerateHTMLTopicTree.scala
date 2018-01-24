package tm.hlta

import org.latlab.io.bif.BifParser
import java.io.FileInputStream
import org.latlab.model.LTM
import org.latlab.util.DataSet
import collection.JavaConversions._
import java.io.PrintWriter
import scala.io.Source
import tm.util.Tree
import tm.util.TreeList
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import tm.util.FileHelpers
import tm.util.Arguments

object RegenerateHTMLTopicTree {
  
  type TopicTree = TreeList[Topic]
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    val topicFile = trailArg[String]()
    val outputName = trailArg[String]()
    val title = opt[String](default = Some("Topic Tree"), descr = "Title in the topic tree")
    
    val layer = opt[List[Int]](descr = "Layer number, i.e. 2,3,4")
    
    banner("""RegenerateHTMLTopicTree topics_file output_name [title]"
        |e.g. RegenerateHTMLTopicTree TopicsTable.html output \"Topic Tree\"
        |It generates the """+outputName+""".nodes.js file and uses
        |"""+outputName+""".topics.js and """+outputName+""".titles.js if they exist.""")
        
    verify
    checkDefaultOpts()  
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
    
    run(conf.topicFile(), conf.outputName(), conf.title(), conf.layer.toOption)
  }

  def run(topicsFile: String, outputName: String, title: String, layer: Option[List[Int]] = None) = {
    val order = readIslands(
      FindTopLevelSiblingClusters.getIslandsFileName(outputName))
    var topLevelTrees = TopicTree.readHTML(topicsFile)
    if(layer.isDefined)
      topLevelTrees = topLevelTrees.trimLevels(layer.get)
    topLevelTrees = topLevelTrees.sortRoots { t => order(t.value.name) }
    BuildWebsite(topLevelTrees, ".", outputName, title)
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

//  Moved to TopicTree.scala
//  object Implicits {
//    import JsonTreeWriter.Node
//
//    implicit val topicToNode: (Topic) => Node = (topic: Topic) => {
//      val label = f"${topic.percentage.get}%.3f ${topic.words.mkString(" ")}"
//      val data = f"""name: "${topic.name}", level: ${topic.level.get}, percentage: ${topic.percentage.get}, mi: ${topic.mi.getOrElse(Double.NaN)}"""
//      Node(topic.name, label, data)
//    }
//  }
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

}

//  Moved to TopicTree.scala
//object JsonTreeWriter {
//  case class Node(id: String, label: String, data: String)
//
//  def writeJsOutput[T](trees: Seq[Tree[T]], outputFile: String)(implicit convert: (T) => Node) = {
//    val writer = new PrintWriter(outputFile)
//    writer.print("var nodes = [")
//    writer.println(trees.map(treeToJs(_, 0)).mkString(", "))
//    writer.println("];")
//    writer.close
//  }
//
//  def treeToJs[T](tree: Tree[T], indent: Int)(implicit convert: (T) => Node): String = {
//    val node = convert(tree.value)
//    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
//    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)
//
//    val children = tree.children.map(treeToJs(_, indent + 4))
//
//    val js = """{
//      |  id: "%s", text: "%s", state: { opened: true, disabled: false, selected: false}, data: { %s }, li_attr: {}, a_attr: {}, children: [%s]
//      |}""".format(node.id, node.label, node.data, children.mkString(", "))
//      .replaceAll(" +\\|", " " * indent)
//
//    js
//  }
//  
//  def writeJsonOutput[T](trees: Seq[Tree[T]], outputFile: String)(implicit convert: (T) => Node) = {
//    val writer = new PrintWriter(outputFile)
//    writer.print("[")
//    writer.println(trees.map(treeToJson(_, 0)).mkString(", "))
//    writer.println("]")
//    writer.close
//  }
//
//  def treeToJson[T](tree: Tree[T], indent: Int)(implicit convert: (T) => Node): String = {
//    val node = convert(tree.value)
//    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
//    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)
//
//    val children = tree.children.map(treeToJson(_, indent + 4))
//
//    val json = """{
//      |  "id": "%s", "text": "%s", "data": {%s}, "children": [%s]
//      |}""".format(node.id, node.label, node.data, children.mkString(", "))
//      .replaceAll(" +\\|", " " * indent)
//
//    json
//  }
//}

object BuildWebsite{
  
  def apply(topicTree: TopicTree, dir: String, outputName: String, title: String, assignment: Option[Assignment] = None){
    topicTree.saveAsJs(outputName + ".nodes.js")
    writeHtmlOutput(title, outputName, outputName + ".html")
    copyAssetFiles(Paths.get(dir))
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
  //  }
}