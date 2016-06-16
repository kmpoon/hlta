package tm.hlta

import tm.hlta.TitleFile.Document
import tm.util.Data
import scala.collection.immutable.TreeMap
import scala.collection.IterableLike
import scala.collection.generic.CanBuildFrom

/**
 * Provides functions for analyzing the topics.
 */
object TopicAnalysis {
  class Context(name: String) {
    lazy val titles = TitleFile.readDocuments(name + ".files.txt").toVector
    lazy val assignment = Reader.readData(name + ".topics.arff")
    lazy val topics = TopicTable.read(name + ".TopicsTable.html")
    lazy val data = Reader.readData(name + ".data.arff")
  }

  /**
   * Returns titles of which the corresponding element in the selection is true.
   */
  def findTitles[C <: Iterable[Document]](
    titles: C with IterableLike[Document, C],
    selection: Iterable[Boolean])(implicit cbf: CanBuildFrom[C, Document, C]) = {
    titles.zip(selection).filter(_._2).map(_._1)
  }

  /**
   * Finds the titles of documents that contains a given word.
   */
  def findTitlesContaining(word: String)(implicit c: Context): Iterable[Document] = {
    findTitlesContaining(c.data, c.titles, word)
  }

  def findTitlesContaining(data: Data,
    titles: Vector[Document], word: String): Iterable[Document] = {
    val index = data.variables.indexWhere(_.getName == word)
    findTitles(titles, data.instances.map(_.values(index) > 0))
  }

  def findTitlesBelongingTo(topic: String)(implicit context: Context) = {
    import context._
    val index = assignment.variables.indexWhere(_.getName == topic)
    findTitles(titles, assignment.instances.map(_.values(index) > 0.5))
  }

  def countByConference(topic: String)(implicit c: Context) = {
    findTitlesBelongingTo(topic).groupBy(_.conference).toMap.mapValues(_.size)
  }

  def countByYear[C <: Iterable[Int]](
    word: String, years: C with IterableLike[Int, C])(
      implicit c: Context, cbf: CanBuildFrom[C, Int, C]): C = {
    val titlesByYear = findTitlesContaining(word)
      .groupBy(_.year).map(p => p._1 -> p._2.size).toMap
    years.map(titlesByYear.getOrElse(_, 0))
  }

  def countByYear(word: String)(implicit c: Context): Seq[Int] = {
    countByYear(word, rangeOfYears)
  }

  def rangeOfYears(implicit c: Context) = {
    val years = c.titles.map(_.year)
    years.min to years.max
  }
}