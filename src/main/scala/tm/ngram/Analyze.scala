package tm.ngram

import tm.text.Dictionary
import tm.util.ParMapReduce.mapReduce
import scalaz.Scalaz._
import tm.util.Arguments
import org.rogach.scallop.Subcommand
import tm.hlta.HTMLTopicTable
import tm.text.NGram
import tm.util.Tree
import tm.hlta.TopicTree
import tm.hlta.Topic

import scala.language.reflectiveCalls

object Analyze {
  class Conf(args: Seq[String]) extends Arguments(args) {
    val count = new Subcommand("count") {
      val dictionary = trailArg[String](descr = "Dictionary file")
    }
    addSubcommand(count)

    val root = new Subcommand("root") {
      val topicFile = trailArg[String](descr = "Topic file (e.g. TopicsTable.html)")
    }
    addSubcommand(root)

    verify()
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
    conf.subcommand match {
      case Some(conf.count) => count(conf.count.dictionary())
      case Some(conf.root)  => findRoot(conf.root.topicFile())
      case _ =>
        throw new IllegalArgumentException(conf.subcommand.toString())
    }
  }

  def count(dictionaryFile: String) = {
    val dict = Dictionary.read(dictionaryFile)
    val counts = countTokensWithEachWord(dict).sortBy(-_._2)

    counts.filter(_._2 > 5).foreach { c =>
      val w = c._1
      val tokens = dict.info.filter(_.token.words.contains(w)).sortBy(-_.tfidf)
      println(w)
      println(tokens.map(_.token).mkString(", "))
      println
    }
  }

  def countTokensWithEachWord(d: Dictionary) = {
    d.info.par.flatMap(_.token.words.map(t => Map(t -> 1))).reduce(_ |+| _).toSeq
  }

  def findRoot(topicFile: String) = {
    val topLevelTrees = HTMLTopicTable.readTopicTree(topicFile)

    def findDistinctWords(tree: Tree[Topic]): Seq[String] = {
      tree.toList.filter(_.level == 1) // list of topics
        .flatMap(_.words.map(w => NGram.fromConcatenatedString(w.w))) // list of ngrams
        .flatMap(_.words) // list of words
        .distinct
    }

    val wordToNumberOfTree = topLevelTrees.roots
      .map(t => findDistinctWords(t).map(_ -> 1).toMap)
      .reduce(_ |+| _)

    println(wordToNumberOfTree.toList.filter(_._2 > 1).sortBy(_._2).mkString("\n"))
  }
}
