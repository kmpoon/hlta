package tm.hlta

import tm.util.Arguments
import org.latlab.util.DataSet
import org.latlab.util.DataSetLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ComputeTopicCoherence {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner(s"Usage: ${ComputeTopicCoherence.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... data-file topic-file")
    val excludeLevels = opt[List[Int]](descr = "List of levels to be excluded.")
    val numWords = trailArg[Int](descr = "Number of words to be used for each topic.")
    val dataFile = trailArg[String](descr = "Data file.")
    val topicFile = trailArg[String](descr = "Topic file in HTML format.  " +
      "The file is named TopicsTable.html in the output directory when tm.hlta.ExtractTopics is run.")

    verify()
    checkDefaultOpts()
  }
  
  val logger = LoggerFactory.getLogger(this.getClass)
  val NameRegex = raw"(.*?)\._(\w{6})\.TopicsTable.html".r

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val numberOfWords = conf.numWords()
    val topicFile = conf.topicFile()
    val topLevelTrees = HTMLTopicTable.readTopicTree(topicFile)
    val data = new DataSet(DataSetLoader.convert(conf.dataFile()));
    
    val name = topicFile match {
      case NameRegex(n, c) => n
      case _ => topicFile
    }
    
    logger.info("Name: {}", name)
    logger.info("Topic table file: {}", topicFile)
    logger.info("Number of words: {}", numberOfWords)
    logger.info("Excluded level(s): {}", conf.excludeLevels.toOption.map(_.mkString(", ")).getOrElse("None"))
    logger.info("Data file: {}", conf.dataFile())

    def shouldExcludeByLevels(excludedLevels: Option[Seq[Int]])(topic: Topic) =
      excludedLevels.map(_.contains(topic.level)).getOrElse(false)

    val topics = topLevelTrees
      .toList()
      .filterNot(shouldExcludeByLevels(conf.excludeLevels.toOption))
      .filter(_.words.size >= numberOfWords)
    val numberOfTopics = topics.size

    def computeCoherence(topic: Topic): Double = {
      val scores = for {
        i <- 1 until (numberOfWords)
        j <- 0 until i
      } yield ComputeTopicCoherenceImpl.computeCoherenceScore(
        data, topic.words(i).w, topic.words(j).w)
      scores.sum
    }

    val sum = topics.par.map(computeCoherence).sum
    val perTopicCoherence = sum / numberOfTopics
    println(s"Per-topic coherence: ${perTopicCoherence}")
    println(s"Number of included topics: ${numberOfTopics}")
    println
    println(List(name, topicFile, perTopicCoherence, numberOfTopics).mkString(","))
  }

}

object SelectTopics {
  def main(args: Array[String]) {
    val topLevelTrees = HTMLTopicTable.readTopicTree(args(0))
    val numberOfWords = args(1).toInt

    topLevelTrees.toList.filter(_.words.size >= numberOfWords).foreach { t =>
      println(t.words.map(_.w).mkString(" "))
    }
  }
}