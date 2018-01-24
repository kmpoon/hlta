package tm.text

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
}

private class StringOrNGram[T]
private object StringOrNGram {
  implicit object StringWitness extends StringOrNGram[String]
  implicit object NGramWitness extends StringOrNGram[NGram]
}

class Sentence(val tokens: Seq[NGram])

object Sentence {
  /**
   * A work around for overloading with func(Seq[A]) and func(Seq[B]) in scala
   */
  def apply[T: StringOrNGram](ts: Seq[T]) = ts.head match{
    case _: String => fromStrings(ts.asInstanceOf[Seq[String]])
    case _: NGram => new Sentence(ts.asInstanceOf[Seq[NGram]])
  }
  
  def fromStrings(ts: Seq[String]): Sentence = new Sentence(ts.map(NGram.apply))
//  def apply(text: String)(implicit asciiOnly: Boolean): Sentence =
//    new Sentence(Preprocessor.tokenizeBySpace(Preprocessor.preprocess(text, 3, asciiOnly = asciiOnly)).map(NGram.apply))
}

case class Document(val sentences: Seq[Sentence])

object Document {
  /**
   * A work around for overloading with func(Seq[A]) and func(Seq[B]) in scala
   */
  def apply[T: StringOrNGram](ts: Seq[T]) = ts.head match{
    case _: String => fromStrings(ts.asInstanceOf[Seq[String]])
    case _: NGram => fromNGrams(ts.asInstanceOf[Seq[NGram]])
  }
  def fromNGrams(ts: Seq[NGram]): Document = new Document(Seq(Sentence(ts)))
  def fromStrings(ts: Seq[String]): Document = new Document(Seq(Sentence(ts)))
}
