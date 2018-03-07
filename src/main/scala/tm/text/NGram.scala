package tm.text

import java.nio.file.Path
import scala.io.Source
import tm.util.manage
import java.util.Collections
import java.io.FileInputStream

case class NGram(val words: Seq[String]) {
  lazy val identifier = words.mkString(NGram.separator)

  override def toString() = identifier
  
  def involves(subNgram: NGram) = words.containsSlice(subNgram.words)
}

object NGram {
  def apply(s: String): NGram = NGram.fromWords(s)

  def fromConcatenatedString(s: String) = NGram(s.split(separator))
  def fromWords(words: String*) = new NGram(Seq(words: _*))
  def fromNGrams(tokens: Seq[NGram]) = new NGram(tokens.flatMap(_.words))

  val separator = "-"

  def readFile(file: String)(enc: String): Seq[NGram] = {
    manage(Source.fromFile(file)(enc))(s =>
      s.getLines().map(NGram.fromConcatenatedString).toIndexedSeq)
  }
}

private class StringOrNGram[T]
private object StringOrNGram {
  implicit object StringWitness extends StringOrNGram[String]
  implicit object NGramWitness extends StringOrNGram[NGram]
}

case class Sentence(val tokens: Seq[NGram]){
  override def toString() = tokens.mkString(" ")
}

object Sentence {
  /**
   * A work around for overloading with func(Seq[A]) and func(Seq[B]) in scala
   * Overloading apply this way probably not the best way to mimic a constructor
   * Open for discussion
   */
  def apply[T: StringOrNGram](ts: Seq[T]) = {
    if(ts.isEmpty)
      new Sentence(Seq.empty[NGram])
    else
      ts.head match{
        case _: String => fromWords(ts.asInstanceOf[Seq[String]])
        case _: NGram => new Sentence(ts.asInstanceOf[Seq[NGram]])
      }
  }
  
  def fromWords(ts: Seq[String]): Sentence = new Sentence(ts.map(NGram.apply))
}

//Force calling Document(Sentence(words)) to expose the possibility that n-gram could form across an actual sentence
//i.e. doing Document("It is sunny. Let us go out.") should possibly form "sunny-let"
case class Document(val sentences: Seq[Sentence]){
  override def toString() = sentences.mkString(". ")
}

object Document {
  def apply(sentence: Sentence) = new Document(Seq(sentence))
}
