package tm.text

import java.nio.file.Paths

import scala.collection.JavaConversions._

/**
 * Used to split sentences, tag POS, and lemmatize.
 */
object StanfordLemmatizer {
  val brackets = Set("-lrb-", "-rrb-", "-lcb-", "-rcb-", "-lsb-", "-rsb-")
  val bracketRegex = ("-(" + brackets.map(_.substring(1, 4)).mkString("|") + ")-").r

  def main(args: Array[String]) {
    if (args.length < 1)
      println("StanfordLemmatizer pdf-file")
    else {
      run(args(0))
    }
  }

  def run(filename: String) = {
    import tm.corpus.pdf.ExtractText
    val d = process(ExtractText.extractSingleText(Paths.get(filename)))
    println(d.sentences.map(_.tokens.mkString(", ")).mkString("\n"))
  }

//<<<<<<< HEAD
//  def process(text: String): Document = {
//    val document = new edu.stanford.nlp.simple.Document(text)
//    val sentences = document.sentences.map(_.lemmas.map(NGram.apply)).map(Sentence.apply(_))
//    Document(sentences)
//=======
  def process(text: String, lemmatization: Boolean = true,
    sentenceSplitting: Boolean = true): Document = {

    val ss: Seq[edu.stanford.nlp.simple.Sentence] =
      if (sentenceSplitting)
        new edu.stanford.nlp.simple.Document(text).sentences
      else
        new edu.stanford.nlp.simple.Sentence(text) :: Nil

    def lemmatize(s: edu.stanford.nlp.simple.Sentence) =
      if (lemmatization) s.lemmas()
      else s.words

    val sentences = ss.map(lemmatize).map(ts => Sentence(ts.map(NGram.apply)))
    Document(sentences.toSeq)
//>>>>>>> f44974702e26234529c78677032e06e3b39a691e
  }

  def processAsSentence(s: String): Sentence = {
    if (s.isEmpty)
      Sentence(Seq.empty[NGram])
    else {
      val sentence = new edu.stanford.nlp.simple.Sentence(s)
      Sentence(sentence.lemmas.map(NGram.apply))
    }
  }
}
