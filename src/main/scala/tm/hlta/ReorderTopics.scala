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

object ReorderTopics {
  case class Topic(name: String, level: Int, indent: Int,
    percentage: Double, mi: Double, words: Seq[String])

  val lineRegex = """<p level ="([^"]*)" name ="([^"]*)" parent = "([^"]*)" percentage ="([^"]*)" (?:MI = "([^"]*)" )?style="text-indent:(.+?)em;"> ([.0-9]+) (.*?)</p>""".r

  def main(args: Array[String]) {
    if (args.length < 1) {
      printUsage()
    } else {
      val topLevelDataName = "top"
      val outputHtmlFileName = "tree.html"
      // computeTopLevelTopic(args(0), args(1), topLevelDataName)
      // findTopLevelSiblings(topLevelDataName + ".txt", "islands.txt")
      val order = readIslands("islands.txt")
      copyAssetFiles(outputHtmlFileName)
      generateTopicTree(args(0), "tree.html", order)
    }
  }

  def printUsage() = {
    //    println("ReorderTopics model_file data_file")
    println("ReorderTopics topics_file")
    println
    println("topics_file: e.g. TopicsTable.html")
  }

  def readModelAndData(modelFile: String, dataFile: String) = {
    val model = new LTM()
    new BifParser(new FileInputStream(modelFile), "UTF-8").parse(model)

    val data = new DataSet(dataFile).synchronize(model)
    (model, data)
  }

  def findTopLevelSiblings(dataFile: String, outputFile: String) = {
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

  def computeTopLevelTopic(modelFile: String, dataFile: String, outputName: String) = {
    val (model, data) = readModelAndData(modelFile, dataFile)

    println(data.getVariables.length)
    println(data.getData.size())

    val levels = HLTA.readLatentVariableLevels(model).toList.groupBy(_._2)
    val top = levels(levels.keys.max).map(_._1)

    //    val topLevelData = PEMTools.HardAssignment(data, model, top.toArray)
    val topLevelData = HLTA.hardAssignment(data, model, top.toArray)
    topLevelData.save(outputName + ".txt")
    topLevelData.saveAsArff(outputName + ".arff", false)

    println(top.map(_.getName).mkString(", "))
  }

  def readTopics(topicsFile: String) = {
    val input = Source.fromFile(topicsFile)
    val lines = input.getLines
      .dropWhile(_ != """<div class="div">""").drop(1)
    try {
      lines.map {
        _ match {
          case lineRegex(level, name, parent, percentage, mi,
            indent, percent1, words) =>
            val miDouble = if (mi == null) Double.NaN else mi.toDouble
            (Topic(name = name, level = level.toInt, indent = indent.toInt,
              percentage = percentage.toDouble, mi = miDouble,
              words = words.split("\\s+")), parent)
        }
      }.toList
    } finally {
      input.close
    }
  }

  /**
   * Constructs a variable to index mapping for ordering the top level variables
   * by reading the islands file.
   */
  def readIslands(islandsFileName: String) = {
    Source.fromFile(islandsFileName).getLines
      .flatMap(_.split(","))
      .zipWithIndex.toMap
  }

  def generateTopicTree(topicsFile: String,
    outputFile: String, topLevelOrder: Map[String, Int]) = {
    val topics = readTopics(topicsFile)
    val ltmTrees = buildLTMTrees(topics)
    val topLevelTrees =
      buildTopicTree(ltmTrees).sortBy { t => topLevelOrder(t.value.name) }
    val parents = topLevelTrees.map(t =>
      topLevelTrees.find(_.getChild(t.value).isDefined)
        .map(_.value.name).getOrElse("none"))
    val content = topLevelTrees.zip(parents).foldLeft("") {
      (s, p) => s + treeToHtml(p._1, p._2, 4)
    }

    writeHtmlOutput(content, outputFile)
  }

  def writeHtmlOutput(content: String, outputFile: String) = {
    val template = Source.fromInputStream(
      this.getClass.getResourceAsStream("/jstree/template.html"))
      .getLines.mkString("\n")

    val writer = new PrintWriter(outputFile)
    writer.println(template.replace("<!-- template-placeholder -->", content))
    writer.close

  }

  def buildLTMTrees(topics: List[(Topic, String)]): List[Tree[Topic]] = {
    val topicToChildrenMap = topics.groupBy(_._2).map {
      _ match {
        case (parent, childPairs) => (parent, childPairs.map(_._1))
      }
    }

    /**
     * Constructs a tree of topics based on the latent tree model.  In this
     * tree, the top level nodes may contain a child of the same level.
     */
    def constructLTMTree(topic: Topic): Tree[Topic] =
      topicToChildrenMap.get(topic.name) match {
        case Some(children) =>
          Tree(topic, children.map(constructLTMTree))
        case None => Tree.leaf(topic)
      }

    val roots = topics.filter(_._2 == "none").map(_._1)
    roots.map(constructLTMTree)
  }

  def buildTopicTree(ltmTrees: List[Tree[Topic]]): List[Tree[Topic]] = {
    val topLevel = ltmTrees.map(_.value.level).max
    val topLevelTrees = ltmTrees.flatMap(_.findSubTrees { _.level == topLevel })

    def filterSameLevelChildren(tree: Tree[Topic]): Tree[Topic] = {
      import tree.{ value => v }
      Tree(v, tree.children.filter(_.value.level < v.level)
        .map(filterSameLevelChildren))
    }

    topLevelTrees.map(filterSameLevelChildren)
  }

  def copyAssetFiles(outputFile: String) = {
    val path = Paths.get(outputFile)
    val assetDir = Option(path.getParent).getOrElse(Paths.get(".")).resolve("lib")

    if (!Files.exists(assetDir))
      Files.createDirectories(assetDir)

    def copy(source: String, target: String) = {
      val input = this.getClass.getResourceAsStream(source)
      Files.copy(input, assetDir.resolve(target), StandardCopyOption.REPLACE_EXISTING)
    }
    copy("/jquery/jquery-2.2.3.min.js", "jquery.min.js")
    copy("/jstree/jstree.min.js", "jstree.min.js")
    copy("/jstree/themes/default/style.min.css", "style.min.css")
    copy("/jstree/themes/default/32px.png", "32px.png")
    copy("/jstree/themes/default/40px.png", "40px.png")
  }

  def treeToHtml(tree: Tree[Topic], parent: String, indent: Int): String = {
    import tree.value
    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)

    val start = """<li class="jstree-open" id="%s" >""".format(value.name)
    val content = f"${value.percentage}%.2f ${value.words.mkString(" ")}"
    val end = "</li>"

    if (tree.children.isEmpty)
      " " * indent + start + content + end + "\n"
    else {
      val childIndent = indent + 2

      " " * indent + start + content + "\n" +
        " " * childIndent + "<ul>" + "\n" +
        tree.children.map(treeToHtml(_, value.name, childIndent)).reduce(_ + _) +
        " " * childIndent + "</ul>" + "\n" +
        " " * indent + end + "\n"
    }
  }
}