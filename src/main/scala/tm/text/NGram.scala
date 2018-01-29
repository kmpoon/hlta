package tm.text

import java.nio.file.Path
import scala.io.Source
import tm.util.manage

case class NGram(val words: Seq[String]) {
  lazy val identifier = words.mkString(NGram.separator)

  override def toString() = identifier
}

object NGram {
  def apply(s: String): NGram = NGram.fromWords(s)

  def fromConcatenatedString(s: String) = NGram(s.split(separator))
  def fromWords(words: String*) = new NGram(Seq(words: _*))
  def fromNGrams(tokens: Seq[NGram]) = new NGram(tokens.flatMap(_.words))

  val separator = "-"

  def readFile(file: String): Seq[NGram] = {
    manage(Source.fromFile(file)("UTF-8"))(s =>
      s.getLines().map(NGram.fromConcatenatedString).toIndexedSeq)
  }
}

class Sentence(val tokens: Seq[NGram])

object Sentence {
  def apply(ts: Seq[NGram]): Sentence = new Sentence(ts)
  def apply(text: String): Sentence =
    new Sentence(Preprocessor.tokenizeBySpace(text).map(NGram.apply))
}

case class Document(val sentences: Seq[Sentence])

object Document {
  def apply(text: String) = new Document(Seq(Sentence(text)))
}
