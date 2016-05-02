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

object RegenerateHTMLTopicTree {
  case class Topic(name: String, level: Int, indent: Int,
    percentage: Double, mi: Double, words: Seq[String])

  val lineRegex = """<p level ="([^"]*)" name ="([^"]*)" parent = "([^"]*)" percentage ="([^"]*)" (?:MI = "([^"]*)" )?style="text-indent:(.+?)em;"> ([.0-9]+) (.*?)</p>""".r

  def main(args: Array[String]) {
    if (args.length < 4) {
      printUsage()
    } else {
      val modelFile = args(0)
      val dataFile = args(1)
      val topicsFile = args(2)
      val outputName = args(3)
      val topLevelDataName = s"${outputName}-top"
      val islandsFile = s"${outputName}-islands.txt"
      val outputHtmlFileName = "tree.html"
      computeTopLevelTopic(modelFile, dataFile, topLevelDataName)
      findAndSaveTopLevelSiblings(topLevelDataName + ".txt", islandsFile)
      val order = readIslands(islandsFile)
      copyAssetFiles(outputHtmlFileName)
      generateTopicTree(topicsFile, outputName, order)
    }
  }

  def printUsage() = {
    //    println("ReorderTopics model_file data_file")
    println("ReorderTopics model_file data_file topics_file output_name")
    println
    println("e.g. ReorderTopics model.bif data.txt TopicsTable.html output")
  }

  def findAndSaveTopLevelSiblings(dataFile: String, outputFile: String) = {
    if (fileExists(outputFile)) {
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

  def fileExists(s: String) = Files.exists(Paths.get(s))

  def computeTopLevelTopic(modelFile: String, dataFile: String, outputName: String) = {
    val hlcmDataFile = outputName + ".txt"
    val arffDataFile = outputName + ".arff"

    if (fileExists(hlcmDataFile) && fileExists(arffDataFile)) {
      println(s"Both data files (${hlcmDataFile} and ${arffDataFile}) exist. " +
        "Skipped computing top level topic assignments.")
    } else {
      val (model, data) = Reader.readLTMAndData(modelFile, dataFile)

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
    outputName: String, topLevelOrder: Map[String, Int]) = {
    val topics = readTopics(topicsFile)
    val ltmTrees = buildLTMTrees(topics)
    val topLevelTrees =
      buildTopicTree(ltmTrees).sortBy { t => topLevelOrder(t.value.name) }
    val parents = topLevelTrees.map(t =>
      topLevelTrees.find(_.getChild(t.value).isDefined)
        .map(_.value.name).getOrElse("none"))
    //    val content = topLevelTrees.zip(parents).foldLeft("") {
    //      (s, p) => s + treeToHtml(p._1, p._2, 4)
    //    }

    writeJsonOutput(topLevelTrees, outputName + ".nodes.js")
    writeHtmlOutput(outputName, outputName + ".html")
  }

  def writeHtmlOutput(outputName: String, outputFile: String) = {
    val template = Source.fromInputStream(
      this.getClass.getResourceAsStream("/tm/hlta/template.html"))
      .getLines.mkString("\n")

    val writer = new PrintWriter(outputFile)

    def replace(s: String, target: String) = {
      s.replaceAll(s""""${target}"""", s""""${outputName}.${target}"""")
    }

    val content = Seq("nodes.js", "topics.js", "titles.js")
      .foldLeft(template)(replace)

    writer.print(content)
    writer.close
  }

  def writeJsonOutput(trees: Seq[Tree[Topic]], outputFile: String) = {
    val writer = new PrintWriter(outputFile)
    writer.print("var nodes = [")
    writer.println(trees.map(treeToJson(_, "none", 0)).mkString(", "))
    writer.println("];")
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
      "/tm/hlta/jquery.magnific-popup.min.js",
      "/tm/hlta/jquery.tablesorter.min.js",
      "/tm/hlta/magnific-popup.css",
      "/tm/hlta/custom.js",
      "/tm/hlta/custom.css",
      "/tm/hlta/tablesorter/blue/asc.gif",
      "/tm/hlta/tablesorter/blue/bg.gif",
      "/tm/hlta/tablesorter/blue/desc.gif",
      ("/tm/hlta/tablesorter/blue/style.css", "tablesorter.css"),
      "/tm/hlta/jstree/themes/default/style.min.css",
      "/tm/hlta/jstree/themes/default/32px.png",
      "/tm/hlta/jstree/themes/default/40px.png",
      "/tm/hlta/jstree/themes/default/throbber.gif")
      .foreach(p => copy(p, assetDir))
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

  def treeToJson(tree: Tree[Topic], parent: String, indent: Int): String = {
    import tree.value
    //    val start = """<li level ="%d" name ="%s" parent = "%s" percentage ="%.2f" MI = "%f" indent="%d">"""
    //      .format(value.level, value.name, parent, value.percentage, value.mi, value.indent)

    val label = f"${value.percentage}%.2f ${value.words.mkString(" ")}"
    val data = f"""name: "${value.name}", level: ${value.level}, indent: ${value.indent}, percentage: ${value.percentage}, mi: ${value.mi}"""
    val children = tree.children.map(treeToJson(_, value.name, indent + 4))

    val json = """{
      |  id: "%s", text: "%s", state: { opened: true, disabled: false, selected: false}, data: { %s }, li_attr: {}, a_attr: {}, children: [%s]
      |}""".format(value.name, label, data, children.mkString(", "))
      .replaceAll(" +\\|", " " * indent)

    json
  }

}