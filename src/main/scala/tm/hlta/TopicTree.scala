package tm.hlta

import scala.io.Source
import tm.util.Tree

object TopicTree {
  case class Word(w: String, probability: Option[Double]) {
    override def toString = probability match {
      case Some(p) => f"$w ($p%.03f)"
      case None => w
    }
  }

  object Word {
    def apply(w: String): Word = Word(w, None)
    def apply(w: String, p: Double): Word = Word(w, Some(p))
  }

  case class Topic(name: String, level: Int,
    percentage: Double, mi: Option[Double], words: Seq[Word])
}

object HTMLTopicTable {
  import TopicTree._

  class HTMLTopic(name: String, level: Int,
    val indent: Int, size: Double, mi: Double, words: Seq[Word])
      extends Topic(name, level, size, Some(mi), words)

  val lineRegex = """<p level ="([^"]*)" name ="([^"]*)" parent = "([^"]*)" percentage ="([^"]*)" (?:MI = "([^"]*)" )?style="text-indent:(.+?)em;"> ([.0-9]+) (.*?)</p>""".r
  val wordsWithProbRegex = """\s*(([^ ]+) ([.0-9]+)\s*)*""".r

  def readTopicTree(topicTableFile: String) = {
    val topics = readTopics(topicTableFile)
    val ltmTrees = buildLTMTrees(topics)
    buildTopicTree(ltmTrees)
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
            indent, percent1, words) =>
            val miDouble = if (mi == null) Double.NaN else mi.toDouble
            val ws = words match {
              case wordsWithProbRegex(_*) =>
                words.split("\\s+").grouped(2)
                  .map(xs => Word(xs(0), Some(xs(1).toDouble))).toVector
              case _ => words.split("\\s+").map(Word.apply).toVector
            }
            (new HTMLTopic(name = name, level = level.toInt, indent = indent.toInt,
              size = percentage.toDouble, mi = miDouble, words = ws), parent)
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
          Tree(topic, children.map(constructLTMTree))
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
      Tree(v, tree.children.filter(_.value.level < v.level)
        .map(filterSameLevelChildren))
    }

    topLevelTrees.map(filterSameLevelChildren)
  }

}