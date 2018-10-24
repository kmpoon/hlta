package tm.text

import java.nio.file.Paths
import java.nio.file.Path
import scala.collection.JavaConversions._
import java.io.StringReader
import java.util.Properties

import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.util.logging.RedwoodConfiguration
import tm.text.Preprocessor._

object StanfordNlp{
  //Suppresses stanford nlp unnecessary message
  edu.stanford.nlp.util.logging.RedwoodConfiguration.errorLevel().apply();
  
  private val brackets = Set("-LRB-", "-RRB-", "-LCB-", "-RCB-", "-LSB-", "-RSB-")
  // creates a StanfordCoreNLP object, with tokenization, sentence splitting, POS tagging and lemmatization 
  private lazy val pipeline = {
    val props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
    new StanfordCoreNLP(props);
  }
  private lazy val pipelineNoSsplit = {
    val props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
    props.setProperty("ssplit.isOneSentence", "true")
    new StanfordCoreNLP(props);
  }
  
  /**
   * StanfordNLP English preprocessor
   */
  def EnglishPreprocessor(text: String, minChars: Int = 4, stopwords: StopWords = StopWords.EnglishStopwords, 
      splitSentence: Boolean = true, lemmatization: Boolean = true): Document = {     
    // create an empty Annotation just with the given text
    val document = new CoreDocument(text);
      
    // run all Annotators on this text
    if(splitSentence) pipeline.annotate(document)
    else pipelineNoSsplit.annotate(document)
    
    // these are all the sentences in this document
    // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
    val sentences = document.sentences().map{s =>
      val tokens = s.tokens().filterNot{ x => brackets.contains(x.word)}
        .map{t => 
          if (lemmatization) t.lemma
          else t.word
        }
        .map(_.toLowerCase)
        .map(normalize)
        .map(replaceNonAlnum)
        .filter(withProperLength(minChars))
        .filterNot(stopwords.contains)
      
      Sentence(tokens.map(NGram.apply))
    }
    Document(sentences)
  }
}

/**
 * Used to split sentences, tag POS, and lemmatize.
 * This object shall be replaced by StandfordNlp
 */
@Deprecated
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
