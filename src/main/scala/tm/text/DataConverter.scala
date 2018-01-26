package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter
import scala.annotation.tailrec
import org.slf4j.LoggerFactory
import tm.util.ParMapReduce.mapReduce
import scalaz.Scalaz._
import tm.util.Data

object DataConverter {
  val logger = LoggerFactory.getLogger(DataConverter.getClass)

  type TokenCounts = Map[NGram, Int]

  /**
   * For external call
   */
  def apply(name: String, documents: GenSeq[Document])(
    implicit settings: Convert.Settings): Data = {
    val (countsByDocuments, dictionary) = countTokensWithNGrams(name, documents)
    Data.fromDictionaryAndTokenCounts(dictionary, countsByDocuments.toList, name = name)
  }

  /**
   * Counts the number of tokens with the consideration of n-grams for a n
   * specified in the {@ code settings}.
   */
  def countTokensWithNGrams(name: String, documents: GenSeq[Document])(
      implicit settings: Convert.Settings): (GenSeq[TokenCounts], Dictionary) = {

    @tailrec
    def loop(documents: GenSeq[Document], previous: Option[Dictionary],
      frequentWords: Set[NGram], n: Int): (GenSeq[Document], Dictionary) = {
      logger.info("Counting n-grams (after {} concatentations) in each document", n)

      // construct a sequence with tokens including those in the original
      // sentence and n-grams with that are built from the original tokens after 
      // concatenation
      def appendNextNGram(sentence: Sentence): Seq[NGram] = {
        if (n == 0) sentence.tokens
        else {
          sentence.tokens ++
            buildNextNGrams(sentence.tokens,
              (w: NGram) => previous.get.map.contains(w) || frequentWords.contains(w))
        }
      }

      val (dictionary, currentFrequent) = {
        logger.info("Building Dictionary")
        val whole = buildDictionary(documents, appendNextNGram)

        logger.info("Saving dictionary before selection")
        whole.save(s"${name}.whole_dict-${n}.csv")

        logger.info("Selecting words in dictionary")
        val (selected, frequent) =
          settings.wordSelector.select(whole, documents.size)

        logger.info("Saving dictionary after selection")
        selected.save(s"${name}.dict-${n}.csv")

        (selected, frequent)
      }

      val documentsWithLargerNGrams = if (n == 0)
        documents
      else {
        logger.info("Replacing constituent tokens by n-grams after {} concatenations", n)
        documents.map(_.sentences.map(s =>
          Preprocessor.replaceConstituentTokensByNGrams(s, dictionary.map.contains(_))))
          .map(ss => Document(ss))
      }

      if (n == settings.concatenations) (documentsWithLargerNGrams, dictionary)
      else loop(documentsWithLargerNGrams,
        Some(dictionary), frequentWords ++ currentFrequent, n + 1)
    }

    logger.info("Extracting words")
    val (newDocuments, dictionary) = loop(documents, None, Set.empty, 0)
    (newDocuments.map(countTermFrequencies), dictionary)
  }

  /**
   * Counts number of tokens in the given sequence of words.
   */
  def countTokens(tokens: Seq[NGram]): TokenCounts = {
    if (tokens.isEmpty) {
      Map.empty
    } else {
      mapReduce(tokens.par)(w => Map(w -> 1))(_ |+| _)
    }
  }

  def countTermFrequencies(d: Document): TokenCounts = {
    countTermFrequencies(d, _.tokens)
  }

  def countTermFrequencies(d: Document,
    tokenizer: (Sentence) => Seq[NGram]): TokenCounts = {
    countTokens(d.sentences.flatMap(tokenizer))
  }

  def buildDictionary(documents: GenSeq[Document], tokenizer: (Sentence) => Seq[NGram] = _.tokens) = {
    import Preprocessor._
    import tm.util.ParMapReduce._

    val (tf, df, n) = mapReduce(documents.par) { d =>
      val tf = countTermFrequencies(d, tokenizer)

      // document count (1 for each occurring token)
      val df = tf.mapValues(c => if (c > 0) 1 else 0)

      (tf, df, 1)

    } { (c1, c2) =>
      (c1._1 |+| c2._1, c1._2 |+| c2._2, c1._3 + c2._3)
    }

    //    // compute the counts of original tokens and new n-grams by 
    //    // documents
    //    val countsByDocuments =
    //      // convert each document to tokens
    //      documents.map(_.sentences.flatMap(tokenizer)).map(countTokens)
    //
    //    val termFrequencies = sumWordCounts(countsByDocuments)
    //    val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
    //    val N = countsByDocuments.size
    //
    def buildWordInfo(token: NGram, tf: Int, df: Int) =
      WordInfo(token, tf, df, computeTfIdf(tf, df, n))

    val info = tf.keys.map { w =>
      buildWordInfo(w, tf(w), df(w))
    }

    Dictionary.buildFrom(info)
  }
  
  /**
   * Converts data to bag-of-words representation, based on the given word
   * counts and dictionary.
   */
  def convertToBow(countsByDocuments: GenSeq[TokenCounts], indices: Map[NGram, Int]) = {
    countsByDocuments.map(toBow(indices))
  }
  
  /**
   * Converts data to bag-of-words representation, based on the given word
   * counts and dictionary.
   */
  def toBow(indices: Map[NGram, Int])(counts: TokenCounts): Array[Int] = {
    val values = Array.fill(indices.size)(0)
    counts.foreach { wc =>
      indices.get(wc._1).foreach { i => values(i) = wc._2 }
    }
    values
  }
  
  /**
   * Given a sequence of tokens, build the n-grams based on the tokens.  The
   * n-grams are built from two consecutive tokens.  Besides,
   * the constituent tokens must be contained in the given {@code base} dictionary.
   * It is possible that after c concatenations n-grams, where n=2^c, may appear.
   */
  def buildNextNGrams(tokens: Seq[NGram],
    shouldConsider: (NGram) => Boolean): Iterator[NGram] =
    tokens.sliding(2)
      .filter(_.forall(shouldConsider))
      .map(NGram.fromNGrams(_))
  
//Now use tm.util.Data
//  def saveAsArff(name: String, filename: String,
//    attributeType: AttributeType.Value, words: Seq[String],
//    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {
//
//    val at = attributeType match {
//      case AttributeType.binary => "{0, 1}"
//      case AttributeType.numeric => "numeric"
//    }
//
//    val writer = new PrintWriter(filename)
//
//    writer.println(s"@RELATION ${name}\n")
//
//    words.foreach { w => writer.println(s"@ATTRIBUTE ${w} ${at}") }
//
//    writer.println("\n@DATA")
//
//    countsByDocuments.foreach { xs => writer.println(toBow(xs).mkString(",")) }
//
//    writer.close
//  }
//
//  def saveAsBinaryHlcm(name: String, filename: String, words: Seq[String],
//    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {
//    def binarize(v: Int) = if (v > 0) 1 else 0
//
//    val writer = new PrintWriter(filename)
//
//    writer.println(s"//${filename.replaceAll("\\P{Alnum}", "_")}")
//    writer.println(s"Name: ${name}\n")
//
//    writer.println(s"// ${words.size} variables")
//    words.foreach { w => writer.println(s"${w}: s0 s1") }
//    writer.println
//
//    countsByDocuments.foreach { vs =>
//      writer.println(toBow(vs).map(binarize).mkString(" ") + " 1.0")
//    }
//
//    writer.close
//  }
//
//  def saveAsSparseData(filename: String,
//    countsByDocuments: Seq[TokenCounts], indices: Map[NGram, Int]) = {
//    val writer = new PrintWriter(filename)
//
//    countsByDocuments.zipWithIndex.foreach { p =>
//      val rowId = p._2 + 1 // since the indices from zipWithIndex start with zero
//      // filter out any words not contained in the indices or those with zero counts 
//      p._1.filter(tc => indices.contains(tc._1) && tc._2 > 0)
//        .foreach { tc => writer.println(s"${rowId},${tc._1}") }
//    }
//
//    writer.close
//  }
  
//Moved to tm.text.Convert
//  /**
//   * minDf is computed when the number of documents is given.
//   */
//  class Settings(val concatenations: Int, val minCharacters: Int,
//    val wordSelector: WordSelector, val asciiOnly: Boolean)
//
//  object Settings {
//    def apply(concatenations: Int = 0, minCharacters: Int = 3, minTf: Int = 6,
//      minDf: (Int) => Int = (Int) => 6, asciiOnly: Boolean = true): Settings =
//      new Settings(concatenations, minCharacters,
//        WordSelector.basic(minCharacters, minTf, minDf), asciiOnly)
//
//    def apply(concatenations: Int, minCharacters: Int,
//      wordSelector: WordSelector, asciiOnly: Boolean): Settings =
//      new Settings(concatenations, minCharacters, wordSelector, asciiOnly)
//  }
}