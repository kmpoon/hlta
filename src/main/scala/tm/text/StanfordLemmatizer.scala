package tm.text

import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation
import scala.collection.JavaConversions._
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation
import tm.pdf.ExtractText
import scala.language.implicitConversions
import scala.util.matching.Regex

/**
 * Used to split sentences, tag POS, and lemmatize.
 */
object StanfordLemmatizer {
  val brackets = Set("-lrb-", "-rrb-", "-lcb-", "-rcb-", "-lsb-", "-rsb-")
  val bracketRegex = ("-(" + brackets.map(_.substring(1, 4)).mkString("|") + ")-").r

  def main(args: Array[String]) {
    if (args.length < 1)
      println("Lemmatizer file")
    else {
      run(args(0))
    }
  }

  def run(filename: String) = {
    import StopWords.implicits.default
    val d = process(ExtractText.extractText(filename))
    println(d.sentences.map(_.tokens.mkString(", ")).mkString("\n"))
  }

  def process(text: String): Document = {
    val document = new edu.stanford.nlp.simple.Document(text)
    val sentences = document.sentences
      .map(_.lemmas.map(NGram.apply))
      .map(Sentence.apply)
    new Document(sentences.toSeq)
  }

  def processAsSentence(s: String): Sentence = {
    if (s.isEmpty)
      Sentence(Seq.empty)
    else {
      val sentence = new edu.stanford.nlp.simple.Sentence(s)
      Sentence(sentence.lemmas.map(NGram.apply))
    }
  }
}
