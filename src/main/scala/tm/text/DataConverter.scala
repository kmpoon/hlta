package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter
import scala.annotation.tailrec
import org.slf4j.LoggerFactory

object DataConverter {
  val logger = LoggerFactory.getLogger(DataConverter.getClass)
  object AttributeType extends Enumeration {
    val binary, numeric = Value
  }

  /**
   * minDf is computed when the number of documents is given.
   */
  class Settings(val maxN: Int, val minCharacters: Int,
    val selectWords: WordSelector.Type)

  object Settings {
    def apply(maxN: Int = 2, minCharacters: Int = 3, minTf: Int = 6,
      minDf: (Int) => Int = (Int) => 6): Settings =
      new Settings(maxN, minCharacters,
        WordSelector.basic(minCharacters, minTf, minDf))

    def apply(maxN: Int, minCharacters: Int,
      selectWords: WordSelector.Type): Settings =
      new Settings(maxN, minCharacters, selectWords)
  }

  //  object implicits {
  //    implicit val default = Settings(
  //      maxN = 2, minCharacters = 3, minTf = 6, minDf = (Int) => 6)
  //  }

  type TokenCounts = Map[NGram, Int]

  def convert(name: String, documents: GenSeq[Document])(
    implicit settings: Settings) = {
    import settings._

    val (countsByDocuments, dictionary) =
      countTokensWithNGrams(name, documents)

    //    log("Converting to bow")
    //    val bow = convertToBow(countsByDocuments, dictionary.map)

    val bowConverter = toBow(dictionary.map)(_)

    logger.info("Saving in ARFF format (count data)")
    saveAsArff(name, s"${name}.arff", AttributeType.numeric,
      dictionary.words, countsByDocuments.seq, bowConverter)
    logger.info("Saving in HLCM format (binary data)")
    saveAsBinaryHlcm(name, s"${name}.txt",
      dictionary.words, countsByDocuments.seq, bowConverter)

    logger.info("done")
  }

  /**
   * Counts the number of tokens with the consideration of n-grams for a n
   * specified in the {@ code settings}.
   */
  def countTokensWithNGrams(
    name: String, documents: GenSeq[Document])(
      implicit settings: Settings): (GenSeq[TokenCounts], Dictionary) = {
    import Preprocessor._
    import settings._

    @tailrec
    def loop(documents: GenSeq[Document],
      previous: Option[Dictionary], n: Int): (GenSeq[Document], Dictionary) = {
      logger.info("Counting n-grams (up to {}) in each document", n)

      // construct a sequence with tokens including those in the original
      // sentence and n-grams with specified n that are built from the
      // original tokens
      def appendNextNGram(sentence: Sentence): Seq[NGram] = {
        if (n == 1) sentence.tokens
        else {
          sentence.tokens ++
            buildNextNGrams(sentence.tokens, previous.get, n)
        }
      }

      // compute the counts of original tokens and new n-grams by 
      // documents
      val countsByDocuments =
        // convert each document to tokens
        documents.map(_.sentences.flatMap(appendNextNGram))
          .map(countTokens)

      val dictionary = {
        logger.info("Building Dictionary")
        val whole = buildDictionary(countsByDocuments)

        logger.info("Saving dictionary before selection")
        whole.save(s"${name}.whole_dict-${n}.csv")

        logger.info("Selecting words in dictionary")
        val selected = settings.selectWords(whole, documents.size)

        logger.info("Saving dictionary after selection")
        selected.save(s"${name}.dict-${n}.csv")

        selected
      }

      val documentsWithLargerNGrams = if (n == 1)
        documents
      else {
        logger.info("Replacing constituent tokens by {}-grams", n)
        documents.map(_.sentences.map(s =>
          replaceConstituentTokensByNGrams(
            s, dictionary.map.contains(_), n)))
          .map(ss => new Document(ss))
      }

      if (n == maxN) (documentsWithLargerNGrams, dictionary)
      else loop(documentsWithLargerNGrams, Some(dictionary), n + 1)
    }

    logger.info("Extracting words")
    val (newDocuments, dictionary) = loop(documents, None, 1)
    val countsByDocument =
      newDocuments.map(_.sentences.flatMap(_.tokens))
        .map(countTokens)
    (countsByDocument, dictionary)
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
   * Converts data to bag-of-words representation, based on the given word
   * counts and dictionary.
   */
  def convertToBow(countsByDocuments: GenSeq[TokenCounts], indices: Map[NGram, Int]) = {
    countsByDocuments.map(toBow(indices))
  }

  def saveAsArff(name: String, filename: String,
    attributeType: AttributeType.Value, words: Seq[String],
    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {

    val at = attributeType match {
      case AttributeType.binary => "{0, 1}"
      case AttributeType.numeric => "numeric"
    }

    val writer = new PrintWriter(filename)

    writer.println(s"@RELATION ${name}\n")

    words.foreach { w => writer.println(s"@ATTRIBUTE ${w} ${at}") }

    writer.println("\n@DATA")

    countsByDocuments.foreach { xs => writer.println(toBow(xs).mkString(",")) }

    writer.close
  }

  def saveAsBinaryHlcm(name: String, filename: String, words: Seq[String],
    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {
    def binarize(v: Int) = if (v > 0) 1 else 0

    val writer = new PrintWriter(filename)

    writer.println(s"//${filename}")
    writer.println(s"Name: ${name}\n")

    writer.println(s"// ${words.size} variables")
    words.foreach { w => writer.println(s"${w}: s0 s1") }
    writer.println

    countsByDocuments.foreach { vs =>
      writer.println(toBow(vs).map(binarize).mkString(" ") + " 1.0")
    }

    writer.close
  }

  /**
   * Given a sequence of tokens, build the n-grams based on the tokens.  The
   * n-grams are built from two consecutive tokens.  Only the combined n-grams
   * with the given length {@code n} are kept in the returned collection.  Besides,
   * the constituent tokens must be contained in the given {@code base} dictionary.
   */
  def buildNextNGrams(tokens: Seq[NGram], base: Dictionary, n: Int): Iterator[NGram] =
    tokens.sliding(2)
      .filter(_.forall(base.map.contains))
      .map(NGram.fromNGrams(_))
      .filter(_.words.length == n)
}