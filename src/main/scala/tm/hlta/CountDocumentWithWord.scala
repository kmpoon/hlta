package tm.hlta

import org.latlab.model.LTM
import ConvertTitlesToJSON.Document
import tm.util.Data
import scala.collection.immutable.TreeMap

object CountDocumentWithWord {
  case class State(model: LTM, data: Data, titles: Vector[Document])

  def read(modelFile: String, dataFile: String, titlesFile: String) = {
    val (model, data) = Reader.readLTMAndARFFData(modelFile, dataFile)
    val titles = ConvertTitlesToJSON.readDocuments(titlesFile).toVector

    State(model, data, titles)
  }

  def findTitlesContaining(s: State)(word: String): Seq[Document] = {
    findTitlesContaining(s.data, s.titles)(word)
  }

  def findTitlesContaining(
    data: Data, titles: Vector[Document])(word: String): Seq[Document] = {
    val variableIndex = data.variables.indexWhere(_.getName == word)
    val titleIndices =
      data.instances
        .map(_.values(variableIndex))
        .zipWithIndex
        .filter(_._1 > 0)
        .map(_._2)

    titleIndices.map(titles.apply)
  }

  def countByYear(s: State)(word: String) = {
    val years = s.titles.map(_.year)
    val minYear = years.min
    val maxYear = years.max
    val titlesByYear = findTitlesContaining(s)(word)
      .groupBy(_.year).map(p => (p._1, p._2.size)).toMap
    TreeMap((minYear to maxYear).map(
      y => (y, titlesByYear.getOrElse(y, 0))): _*)
  }
}