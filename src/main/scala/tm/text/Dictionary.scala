package tm.text

import java.io.PrintWriter
import scala.io.Source
import tm.util.CompositeComparator
import java.io.InputStream
import scala.math.Ordered
/**
 * Info about a word.
 *
 * ntf: normalized term frequency (i.e. sum of occurrences/documentLength in all document.
 * df: document frequency (i.e. number of documents with
 */
case class TfidfWordInfo(val token: NGram, tf: Int, df: Int, tfidf: Double) extends GeneralWordInfo with Ordered[TfidfWordInfo]{
  
  def compare(that: TfidfWordInfo) = TfidfWordInfo.comparator(this, that)
  
  def header() = "word,tf,df,tfidf"
  
  override def toString = s"${token.identifier},${tf},${df},${tfidf}"
}

object TfidfWordInfo{
  val comparator = CompositeComparator[TfidfWordInfo](
    (w1, w2) => -w1.tfidf.compareTo(w2.tfidf),
    (w1, w2) => w1.token.identifier.compareTo(w2.token.identifier))
  
  def fromString(s: String) = s.split(",") match {
    case Array(w, tf, df, tfidf) =>
      TfidfWordInfo(
        NGram.fromConcatenatedString(w),
        tf.toInt, df.toInt, tfidf.toDouble)
  }
}

trait GeneralWordInfo{
  val token: NGram
  def header(): String
}

case class DocumentInfo(title: String, time: Int)

object Dictionary {
  /**
   * Builds a dictionary from a collection of WordInfo objects.
   */
  def buildFrom[T <: GeneralWordInfo with Ordered[T]](w: Iterable[T]): Dictionary[T] = {
    val info = w.toVector.sorted
    val map = info.zipWithIndex.map(p => (p._1.token -> p._2)).toMap
    new Dictionary(info, map)
  }

  def save[T <: GeneralWordInfo](filename: String, ws: Iterable[T]) = {
    if(!ws.isEmpty){
      val writer = new PrintWriter(filename)
  
      writer.println(ws.head.header)
      ws.map(i => i.toString())
        .foreach(writer.println)
  
      writer.close
    }
  }

  /**
   * Reads a dictionary from a given source.
   */
  def read[T <: GeneralWordInfo with Ordered[T]](source: Source, f: String => T) = {
    Dictionary.buildFrom(
      source.getLines
        .drop(1) // skip the header
        .map{line => f(line)}
        .toIterable)
  }

  def read[T <: GeneralWordInfo with Ordered[T]](input: InputStream, f: String => T): Dictionary[T] = {
    read(Source.fromInputStream(input), f)
  }

  /**
   * Reads a dictionary from a file specified by the given file name.
   */
  def read[T <: GeneralWordInfo with Ordered[T]](filename: String, f: String => T): Dictionary[T] = {
    read(Source.fromFile(filename), f)
  }
}

class Dictionary[T <: GeneralWordInfo with Ordered[T]](val info: IndexedSeq[T], val map: Map[NGram, Int]) {

  def getInfo(token: NGram) = info(map(token))

  def filter(pred: (T) => Boolean) =
    Dictionary.buildFrom(info.filter(pred))

  def words() = info.map(_.token.identifier)

  def getMap[U](f: (T) => U) = map.mapValues(i => f(info(i)))

  def save(filename: String) = Dictionary.save(filename, info)
}
