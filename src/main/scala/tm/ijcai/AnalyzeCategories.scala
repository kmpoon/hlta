package tm.ijcai

import tm.hlta.TopicAnalysis.Context
import tm.hlta.TopicAnalysis
import tm.util.MIComputer
import tm.util.MIComputer.MutualInformation

/**
 * Computes the association between the discovered topics with the predefined
 * topics in IJCAI-2015
 */
object AnalyzeCategories {
  def main(args: Array[String]) = {
    implicit val context = new Context("aaai_ijcai.20160505")

    val categoriesIn2015 = context.titles.collect(d =>
      getTrackInIJCAI15(d.path) match { case Some(c) => c })
      .groupBy(c => c).mapValues(_.size)

    val total = categoriesIn2015.values.sum

    // computes MI between topic and category
    def computeMI(topic: String): Map[String, MutualInformation] = {
      val countForTopicByCategory = getCountByCategory(topic)
      val countForTopic = countForTopicByCategory.values.sum

      countForTopicByCategory.map(_ match {
        case (category, count) => {
          val c11 = count
          val c10 = categoriesIn2015(category) - count // category = 1, topic = 0 
          val c01 = countForTopic - count // category = 0, topic = 1 
          val c00 = c10 + c01 - c11
          (category, MIComputer.compute(Array(Array(c00, c01), Array(c10, c11))))
        }
      })
    }
    
    val topics = context.topics.map(_._1)
    val topLevel = topics.map(_.level).max

    val selected = topics.filter(_.level == topLevel).map(_.name)
//    val mi = selected.map(computeMI)

//    println(("Category" +: selected).mkString("\t"))
//    categoriesIn2015.foreach {
//      _ match {
//        case (category, count) =>
//          println((
//            category +: mi.map(_.get(category).map(_.normalized).getOrElse(0)))
//            .mkString("\t"))
//      }
//    }
    
//    computeMI("Z78").mapValues.foreach(println)
    TopicAnalysis.findTitlesBelongingTo("Z78").filter(d => d.year == 2015 && d.conference == "ijcai").foreach(d => println(d.path))

    //    val documents = TopicAnalysis.findTitlesBelongingTo("Z53")
    //    documents.map(_.path).flatMap(p => getTrackInIJCAI15(p)).foreach(println)

    //    val categoryCounts = getCountByCategory("Z53")
    //    categoryCounts.foreach(println)
  }

  val mainTrackPattern = """ijcai-2015/Main Track — ([^/]*)""".r

  def getTrackInIJCAI15(path: String): Option[String] =
    for (m <- mainTrackPattern.findFirstMatchIn(path)) yield m.group(1)

  def getCountByCategory(topic: String)(implicit context: Context) = {
    val documents = TopicAnalysis.findTitlesBelongingTo(topic)
    val categories = documents.collect(d =>
      getTrackInIJCAI15(d.path) match { case Some(c) => c })
    categories.groupBy(c => c).mapValues(_.size)
  }
}