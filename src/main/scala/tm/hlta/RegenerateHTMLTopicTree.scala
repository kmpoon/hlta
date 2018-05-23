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
import scala.collection.GenSeq
import org.apache.commons.lang.StringEscapeUtils

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
    var topLevelTrees = TopicTree.readHtml(topicsFile)
    if(layer.isDefined)
      topLevelTrees = topLevelTrees.trimLevels(layer.get)
    topLevelTrees = topLevelTrees.sortRoots { t => order(t.value.name) }
    BuildWebsite(".", outputName, title, topLevelTrees)
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
}


object BuildWebsite{
  
  /**
   * for external call
   * 
   * save all files to js so no ajax.get call is required
   * this allows to read website without setting up a server
   */
  def apply(dir: String, outputName: String, title: String, topicTree: TopicTree = null, catalog: DocumentCatalog = null, 
      docNames: Seq[String] = null, docUrls: Seq[String] = null){
    if(topicTree!=null) topicTree.saveAsJs(dir + "/" + outputName + ".nodes.js", jsVarName = "nodes")
    if(catalog!=null) catalog.saveAsJs(dir + "/" + outputName + ".topics.js", jsVarName = "topicMap")
    if(docNames!=null) writeDocNames(docNames, dir + "/" + s"${outputName}.titles.js", docUrls = docUrls)
    writeHtmlOutput(title, outputName, dir + "/" + outputName + ".html")
    copyAssetFiles(Paths.get(dir))
  }
  
  def writeDocNames(docNames: GenSeq[String], outputFile: String, docUrls: Seq[String] = null) = {
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeJavaScript(str)
    }
    
    val writer = new PrintWriter(outputFile)
    writer.println("var documents = [")
    if(docUrls==null)    writer.println(docNames.map{docName=>"\""+docName.escape+"\""}.mkString(",\n"))
    else    writer.println(docNames.zip(docUrls).map{case(docName, docUrl)=>"[\""+docName.escape+"\",\""+docUrl.escape+"\"]"}.mkString(",\n"))
    writer.println("]")
    writer.close
  }
  
  def writeHtmlOutput(title: String, outputName: String, outputFile: String) = {
    
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeHtml(str)
    }
    
    val template = Source.fromInputStream(
      this.getClass.getResourceAsStream("/tm/hlta/template.html"))
      .getLines.mkString("\n")

    val writer = new PrintWriter(outputFile)

    def replace(s: String, target: String) = {
      s.replaceAll(s""""${target}"""", s""""${outputName}.${target}"""")
    }

    var content = Seq("nodes.js", "topics.js", "titles.js")
      .foldLeft(template)(replace)
    content = content.replaceAll("<!-- title-placeholder -->", title.escape)

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
      println(s"Copy from resource, totally done")
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
}


/**
 * Jstree is a javascript library, see www.jstree.com
 */
object JstreeWriter{
  
  /**
   * Describes how topic is presented in the jstree
   */
  case class Node(id: String, label: String, data: Map[String, Any])
  
  def writeJs[A](roots: Seq[Tree[A]], outputFile: String, jsVarName: String, jstreeContent: A => Node){
    
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeJavaScript(str)
    }

    def _treeToJs(tree: Tree[A], indent: Int): String = {
      val node = jstreeContent(tree.value)
      val children = tree.children.map(_treeToJs(_, indent + 4))
      val js = """{
      |  id: "%s", text: "%s", data: { %s }, children: [%s]
      |}""".format(node.id.escape, node.label.escape, node.data.map{
        case (variable: String, value: String) => variable.escape+": \""+value.escape+"\""
        case (variable: String, value: Number) => variable.escape+": "+value
        case (variable: String, value) => variable.escape+": "+value
        }.mkString(", "), children.mkString(", "))
        .replaceAll(" +\\|", " " * indent)
      js
    }
    
    val writer = new PrintWriter(outputFile)
    writer.print("var "+jsVarName+" = [")
    writer.println(roots.map(_treeToJs(_, 0)).mkString(", "))
    writer.println("];")
    writer.close
  }

  def writeJson[A](roots: Seq[Tree[A]], outputFile: String, jstreeContent: A => Node){
    
    def _treeToJson(tree: Tree[A], indent: Int): String = {
      val node = jstreeContent(tree.value)
      val children = tree.children.map(_treeToJson(_, indent + 4))
      val json = """{
      |  "id": "%s", "text": "%s", "data": {%s}, "children": [%s]
      |}""".format(node.id, node.label, node.data.map{
        case (variable: String, value: Number) => "\""+variable+"\": "+value
        case (variable: String, value: String) => "\""+variable+"\": \""+value+"\""
        case (variable: String, value) => "\""+variable+"\": \""+value+"\""
        }.mkString(", "),  children.mkString(", "))
        .replaceAll(" +\\|", " " * indent)
      json
    }
    
    println(s"will write json file, open writeJson: " + outputFile)
    val writer = new PrintWriter(outputFile)
    writer.print("[")
    writer.println(roots.map(_treeToJson(_, 0)).mkString(", "))
    writer.println("]")
    println(s"writeJson done")
    writer.close
  }

  /**
   * Plain html, no jstree plugin
   * TODO: change this to be the format of Peixian's html
   */
  def writeSimpleHtml[A](roots: Seq[Tree[A]], outputFile: String, jstreeContent: A => Node){
    
    implicit class Escape(str: String){
      def escape = StringEscapeUtils.escapeHtml(str)
    }

    def _treeToHtml(tree: Tree[A], indent: Int): String = {
      val node = jstreeContent(tree.value)
      val start = """<li class="jstree-open" id="%s" >""".format(node.id.escape)
      val content = node.label.escape
      val end = "</li>"
  
      if (tree.children.isEmpty)
        " " * indent + start + content + end + "\n"
      else {
        val childIndent = indent + 2
  
        " " * indent + start + content + "\n" +
          " " * childIndent + "<ul>" + "\n" +
          tree.children.map(_treeToHtml(_, childIndent)).reduce(_ + _) +
          " " * childIndent + "</ul>" + "\n" +
          " " * indent + end + "\n"
      }
    }
    
    val writer = new PrintWriter(outputFile)
    writer.println(roots.map(_treeToHtml(_, 0)).reduce(_ + _))
    writer.close
  }
}
