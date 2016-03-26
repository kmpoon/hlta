package tm.text

case class NGram(words: Seq[String]) {
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

class Sentence(val tokens: Seq[NGram])

object Sentence {
    def apply(ts: Seq[NGram]): Sentence = new Sentence(ts)
    def apply(text: String): Sentence =
        new Sentence(Preprocessor.tokenizeBySpace(text).map(NGram.apply))
}

class Document(val sentences: Seq[Sentence])

object Document {
    def apply(text: String) = new Document(Seq(Sentence(text)))
}
