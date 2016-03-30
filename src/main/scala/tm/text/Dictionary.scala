package tm.text

import java.io.PrintWriter
import scala.io.Source
import tm.util.CompositeComparator

/**
 * Info about a word.
 *
 * tf: term frequency (i.e. number of occurrences in all document.
 * df: document frequency (i.e. number of documents with
 */
case class WordInfo(token: NGram, tf: Int, df: Int, tfidf: Double)

object Dictionary {
  private val comparator = CompositeComparator[WordInfo](
    (w1, w2) => -w1.tfidf.compareTo(w2.tfidf),
    (w1, w2) => w1.token.identifier.compareTo(w2.token.identifier))

  /**
   * Builds a dictionary from a collection of WordInfo objects.
   */
  def buildFrom(w: Iterable[WordInfo]) = {
    val info = w.toVector.sortWith(comparator(_, _) < 0)
    val map = info.zipWithIndex.map(p => (p._1.token -> p._2)).toMap
    new Dictionary(info, map)
  }

  /**
   * Reads a dictionary from a file specified by the given file name.
   */
  def read(filename: String) = {
    buildFrom(
      Source.fromFile(filename).getLines
        .drop(1) // skip the header
        .map(_.split(","))
        .map(_ match {
          case Array(w, tf, df, tfidf) =>
            WordInfo(NGram.fromConcatenatedString(w),
              tf.toInt, df.toInt, tfidf.toDouble)
        })
        .toIterable)
  }
}

class Dictionary(val info: IndexedSeq[WordInfo], val map: Map[NGram, Int]) {

  def getInfo(token: NGram) = info(map(token))

  def filter(pred: (WordInfo) => Boolean) =
    Dictionary.buildFrom(info.filter(pred))

  def words() = info.map(_.token.identifier)

  def getMap[T](f: (WordInfo) => T) = map.mapValues(i => f(info(i)))

  def save(filename: String) = {
    val writer = new PrintWriter(filename)

    writer.println("word,tf,df,tfidf")
    info.map(i => s"${i.token.identifier},${i.tf},${i.df},${i.tfidf}")
      .foreach(writer.println)

    writer.close
  }
}
