package tm.text

import java.nio.file.Paths
import tm.util.FileHelpers
import tm.util.ParMapReduce.mapReduce

/**
 * Count number of documents, number of words, vocabulary size, and
 * compute average length of a document.
 */
object Count {
  case class Stats(numDocuments: Int, numWords: Int, words: Set[String]) {
    def +(s: Stats) = Stats(
      numDocuments + s.numDocuments,
      numWords + s.numWords, words ++ s.words)
  }

  def main(args: Array[String]) {
    val source = Paths.get(args(0))
    val paths = FileHelpers.findFiles(source, "txt").map(source.resolve)
    val stat = mapReduce(paths.par)(Convert.readFile { f =>
      val content = f.map(Preprocessor.tokenizeBySpace(_)).flatten
      Stats(1, content.size, content.toSet)
    })(_ + _)

    println(s"Number of documents: ${stat.numDocuments}")
    println(s"Number of words: ${stat.numWords}")
    println(s"Average length: ${stat.numWords.toDouble / stat.numDocuments}")
    println(s"Size of vocabulary: ${stat.words.size}")
  }
}