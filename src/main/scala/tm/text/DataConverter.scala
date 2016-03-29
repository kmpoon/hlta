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
    case class Settings(
        minCharacters: Int, maxN: Int, minTf: Int, minDf: (Int) => Int)

    object implicits {
        implicit val default = Settings(
            minCharacters = 3, maxN = 2, minTf = 6, minDf = (Int) => 6)
    }

    type TokenCounts = Map[NGram, Int]

    def convert(name: String, documents: GenSeq[Document], log: (String) => Any)(
        implicit settings: Settings) = {
        import settings._

        val (countsByDocuments, dictionary) =
            countTokensWithNGrams(name, documents, log)

        log("Converting to bow")
        val bow = convertToBow(countsByDocuments, dictionary.map)

        log("Saving in ARFF format")
        saveAsArff(name, s"${name}.arff",
            AttributeType.numeric, dictionary.words, bow.seq)
        saveAsBinaryHlcm(name, s"${name}.txt", dictionary.words, bow.seq)

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

            log("Building Dictionary")
            val dictionary = buildDictionary(countsByDocuments).filter(w =>
                w.token.words.forall(_.length > minCharacters)
                    && w.tf >= minTf
                    && w.df >= minDf(documents.size))

            log("Saving dictionary")
            dictionary.save(s"${name}.dict-${n}.csv")

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
    def convertToBow(countsByDocuments: GenSeq[TokenCounts], indices: Map[NGram, Int]) = {
        def convert(counts: TokenCounts) = {
            val values = Array.fill(indices.size)(0)
            counts.foreach { wc =>
                indices.get(wc._1).foreach { i => values(i) = wc._2 }
            }
            values
        }

        countsByDocuments.map(convert)
    }

    def saveAsArff(name: String, filename: String,
        attributeType: AttributeType.Value, words: Seq[String],
        bow: Seq[Array[Int]]) = {

        val at = attributeType match {
            case AttributeType.binary => "{0, 1}"
            case AttributeType.numeric => "numeric"
        }

        val writer = new PrintWriter(filename)

        writer.println(s"@RELATION ${name}\n")

        words.foreach { w => writer.println(s"@ATTRIBUTE ${w} ${at}") }

        writer.println("\n@DATA")

        bow.foreach { values => writer.println(values.mkString(",")) }

        writer.close
    }

    def saveAsBinaryHlcm(name: String, filename: String,
        words: Seq[String], bow: Seq[Array[Int]]) = {
        def binarize(v: Int) = if (v > 0) 1 else 0

        val writer = new PrintWriter(filename)

        writer.println(s"//${filename}")
        writer.println(s"Name: ${name}\n")

        writer.println(s"// ${words.size} variables")
        words.foreach { w => writer.println(s"${w}: s0 s1") }
        writer.println

        bow.foreach { values =>
            writer.println(values.map(binarize).mkString(" ") + " 1.0")
        }

        writer.close
    }

}