package tm.hlta

import scala.io.Source
import tm.util.Tree
import tm.util.TreeList
import org.json4s._
import java.util.ArrayList
import java.io.PrintWriter

case class Word(w: String, probability: Option[Double]) {
  override def toString = probability match {
    case Some(p) => f"$w ($p%.03f)"
    case None => w
  }
  
  def toVarName() = w
}

object Word {
  def apply(w: String): Word = Word(w, None)
  def apply(w: String, p: Double): Word = Word(w, Some(p))
}

case class Topic(name: String, level: Option[Int], percentage: Option[Double], mi: Option[Double], words: Seq[Word])

object Topic{
  def apply(name: String, words: Seq[String]): Topic = Topic(name, None, None, None, words.map{ x => Word(x)})
  def apply(name: String, level: Int, words: Seq[String]): Topic = 
    Topic(name, Some(level), None, None, words.map{ x => Word(x)})
  def apply(name: String, level: Int, percentage: Double, mi: Option[Double], words: Seq[Word]): Topic = 
    Topic(name, Some(level), Some(percentage), mi, words)
}

object TopicTree{
  
  def apply(topicTree: Seq[Tree[Topic]]) = new TopicTree(topicTree)
  
  def apply(topicTree: TreeList[Topic]) = new TopicTree(topicTree.roots)
  
  /**
   *  Reads topics and their parents from the specified file.  It returns a list
   *  in which each element is a pair of topic and its parent.
   */
  def readHTML(topicsFile: String) = HTMLTopicTable.readTopicTree(topicsFile)
  
  def readJson(fileName: String) = {
    
    case class Data(name: String, level: Option[Int], percentage: Option[Double], mi: Option[Double])
    case class Node(id: String, text: String, data: Option[Data], children: List[Node])
    import org.json4s.native.JsonMethods._
    
    implicit val formats = DefaultFormats
    val jsonString = Source.fromFile(fileName).mkString
    val entries = parse(jsonString).extract[List[Node]]
    val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
      
    def _constructTree(node: Node): Tree[Topic] = {
      val children = node.children.map { x => _constructTree(x) }
      val text = node.text.split(" ").filter { x => !x.charAt(0).isDigit }
      val topic = {
        if(node.data.isDefined && node.data.get.level.isDefined)
          Topic(node.id, node.data.get.level.get, text)
        else
          Topic(node.id, text)
      }
      Tree.node(topic, children)
    }
    val trees = entries.map{_constructTree(_)}
    new TreeList(trees)
  }
  
}

class TopicTree(roots: Seq[Tree[Topic]]) extends TreeList[Topic](roots){
  
  final private case class Info(id: String, label: String, data: String)

  override def trimLevels(takeLevels: List[Int]) = TopicTree(super.trimLevels(takeLevels).roots)
  
  override def sortRoots[B](f: Tree[Topic] => B)(implicit ord: Ordering[B]) = TopicTree(super.sortRoots(f))

  def saveAsJs(outputFile: String){
    implicit class NodeInfo(topic: Topic){
      def info(): Info = {
        //val label = f"${topic.percentage}%.3f ${topic.words.mkString(" ")}"
        val label = f"${topic.words.mkString(" ")}"
        var data = f"""name: "${topic.name}""""
        if(topic.level.isDefined)         data += f""",level: ${topic.level.get}"""
        if(topic.percentage.isDefined)    data += f""",percentage: ${topic.percentage.get}"""
        if(topic.mi.isDefined)            data += f""",mi: ${topic.mi.get}"""
        Info(topic.name, label, data)
      }
    }
    
    def _treeToJs(tree: Tree[Topic], indent: Int): String = {
      val node = tree.value.info()
      val children = tree.children.map(_treeToJs(_, indent + 4))
      val js = """{
      |  id: "%s", text: "%s", data: { %s }, children: [%s]
      |}""".format(node.id, node.label, node.data, children.mkString(", "))
        .replaceAll(" +\\|", " " * indent)
      js
    }
    
    val writer = new PrintWriter(outputFile)
    writer.print("var nodes = [")
    writer.println(roots.map(_treeToJs(_, 0)).mkString(", "))
    writer.println("];")
    writer.close
  }

  def saveAsJson(outputFile: String){
    implicit class NodeInfo(topic: Topic){
      def info(): Info = {
        //val label = f"${topic.percentage}%.3f ${topic.words.mkString(" ")}"
        val label = f"${topic.words.mkString(" ")}"
        var data = f""""name": "${topic.name}""""
        if(topic.level.isDefined)         data += f""","level": ${topic.level.get}"""
        if(topic.percentage.isDefined)    data += f""","percentage": ${topic.percentage.get}"""
        if(topic.mi.isDefined)            data += f""","mi": ${topic.mi.get}"""
        Info(topic.name, label, data)
      }
    }
    
    def _treeToJson(tree: Tree[Topic], indent: Int): String = {
      val node = tree.value.info()
      val children = tree.children.map(_treeToJson(_, indent + 4))
      val json = """{
      |  "id": "%s", "text": "%s", "data": {%s}, "children": [%s]
      |}""".format(node.id, node.label, node.data, children.mkString(", "))
        .replaceAll(" +\\|", " " * indent)
      json
    }
    
    val writer = new PrintWriter(outputFile)
    writer.print("[")
    writer.println(roots.map(_treeToJson(_, 0)).mkString(", "))
    writer.println("]")
    writer.close
  }
}

object HTMLTopicTable {

  class HTMLTopic(name: String, level: Int,
    val indent: Int, size: Double, mi: Option[Double], words: Seq[Word])
      extends Topic(name, Some(level), Some(size), mi, words)

  val lineRegex = """<p level ="([^"]*)" name ="([^"]*)" parent = "([^"]*)" (?:percentage ="([^"]*)" )?(?:MI = "([^"]*)" )?style="text-indent:(.+?)em;"> ([.0-9]+) (.*?)</p>""".r
  val wordsWithProbRegex = """\s*(([^ ]+) ([.0-9]+)\s*)*""".r

  def readTopicTree(topicTableFile: String) = {
    val topics = readTopics(topicTableFile)
    val ltmTrees = buildLTMTrees(topics)
    TopicTree(buildTopicTree(ltmTrees))
  }

  /**
   *  Reads topics and their parents from the specified file.  It returns a list
   *  in which each element is a pair of topic and its parent.
   */
  def readTopics(topicsFile: String): List[(HTMLTopic, String)] = {
    val input = Source.fromFile(topicsFile)
    val lines = input.getLines
      .dropWhile(_ != """<div class="div">""")
      .drop(1) // drop the above <div> line
      .takeWhile(_ != """</div>""")

    try {
      lines.map {
        _ match {
          case lineRegex(level, name, parent, percentage, mi,
            indent, percentage1, words) =>
            val miDouble = if (mi == null) None else Some(mi.toDouble)
            val ws = words match {
              case wordsWithProbRegex(_*) =>
                words.split("\\s+").grouped(2)
                  .map(xs => Word(xs(0), Some(xs(1).toDouble))).toVector
              case _ => words.split("\\s+").map(Word.apply).toVector
            }
            (new HTMLTopic(name = name, level = level.toInt, indent = indent.toInt,
              size = percentage1.toDouble, mi = miDouble, words = ws), parent)
        }
      }.toList
    } finally {
      input.close
    }
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
          new Tree(topic, children.map(constructLTMTree))
        case None => Tree.leaf(topic)
      }

    val roots = topics.filter(_._2 == "none").map(_._1)
    roots.map(constructLTMTree)
  }

  /**
   * Builds topic trees such that all top-level topics are used as roots.
   */
  def buildTopicTree(ltmTrees: List[Tree[Topic]]): List[Tree[Topic]] = {
    // find all top-level topics
    val topLevel = ltmTrees.map(_.value.level).max
    val topLevelTrees = ltmTrees.flatMap(_.findSubTrees { _.level == topLevel })

    // Filter away children of the same level.  This happens when the top level
    // topics are connected as a tree.
    def filterSameLevelChildren(tree: Tree[Topic]): Tree[Topic] = {
      import tree.{ value => v }
      new Tree(v, tree.children.filter(_.value.level.get < v.level.get)
        .map(filterSameLevelChildren(_)))
    }

    topLevelTrees.map(filterSameLevelChildren)
  }

}