package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter

object DataConverter {
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

  object implicits {
    implicit val default = Settings(
      maxN = 2, minCharacters = 3, minTf = 6, minDf = (Int) => 6)
  }

  type TokenCounts = Map[NGram, Int]

  def convert(name: String, documents: GenSeq[Document], log: (String) => Any)(
    implicit settings: Settings) = {
    import settings._

    val (countsByDocuments, dictionary) =
      countTokensWithNGrams(name, documents, log)

    //    log("Converting to bow")
    //    val bow = convertToBow(countsByDocuments, dictionary.map)

    val bowConverter = toBow(dictionary.map)(_)

    log("Saving in ARFF format (count data)")
    saveAsArff(name, s"${name}.arff", AttributeType.numeric,
      dictionary.words, countsByDocuments.seq, bowConverter)
    log("Saving in HLCM format (binary data)")
    saveAsBinaryHlcm(name, s"${name}.txt",
      dictionary.words, countsByDocuments.seq, bowConverter)

    log("done")
  }

  /**
   * Counts the number of tokens with the consideration of n-grams for a n
   * specified in the {@ code settings}.
   */
  def countTokensWithNGrams(
    name: String, documents: GenSeq[Document], log: (String) => Any)(
      implicit settings: Settings): (GenSeq[TokenCounts], Dictionary) = {
    import Preprocessor._
    import settings._

    def rec(documents: GenSeq[Document], n: Int): (GenSeq[Document], Dictionary) = {
      log(s"Counting n-grams (up to ${n}) in each document")

      // construct a sequence with tokens including those in the original
      // sentence and n-grams with specified n that are built from the
      // original tokens
      def appendNextNGram(sentence: Sentence): Seq[NGram] =
        sentence.tokens ++ buildNextNGrams(sentence.tokens, n)

      // compute the counts of original tokens and new n-grams by 
      // documents
      val countsByDocuments =
        // convert each document to tokens
        documents.map(_.sentences.flatMap(appendNextNGram))
          .map(countTokens)

      val dictionary = {
        log("Building Dictionary")
        val whole = buildDictionary(countsByDocuments)

        log("Saving dictionary before selection")
        whole.save(s"${name}.whole_dict-${n}.csv")

        log("Selecting words in dictionary")
        val selected = settings.selectWords(whole, documents.size)

        log("Saving dictionary after selection")
        selected.save(s"${name}.dict-${n}.csv")

        selected
      }

      log(s"Replacing constituent tokens by ${n}-grams")
      val documentsWithLargerNGrams =
        documents.map(_.sentences.map(s =>
          replaceConstituentTokensByNGrams(
            s, dictionary.map.contains(_), n)))
          .map(ss => new Document(ss))

      if (n == maxN) (documentsWithLargerNGrams, dictionary)
      else rec(documentsWithLargerNGrams, n + 1)
    }

    log("Extracting words")
    //        val words = documents.map(tokenizeBySpace(_).map(NGram(_)))
    //
    //        val (tokens, dictionary) = rec(words, 2)
    //        (tokens.map(countWords), dictionary)
    val (newDocuments, dictionary) = rec(documents, 2)
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

    countsByDocuments.foreach { vs => writer.println(toBow(vs).mkString(",")) }

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
}