package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter

object DataConverter {
    object AttributeType extends Enumeration {
        val binary, numeric = Value
    }

    case class Settings(minCharacters: Int, maxN: Int, minTf: Int, minDf: Int)

    object implicits {
        implicit val default =
            Settings(minCharacters = 3, maxN = 2, minTf = 6, minDf = 6)
    }

    type TokenCounts = Map[NGram, Int]

    def convert(name: String, documents: GenSeq[String], log: (String) => Any)(
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

    def countTokensWithNGrams(
        name: String, documents: GenSeq[String], log: (String) => Any)(
            implicit settings: Settings) = {
        import Preprocessor._
        import settings._
        import StopWords.implicits.default

        def rec(tokens: GenSeq[Seq[NGram]], n: Int): (GenSeq[Seq[NGram]], Dictionary) = {
            log(s"Counting n-grams (up to ${n}) in each document")
            // compute the counts of original tokens and new n-grams by 
            // documents
            val countsByDocuments =
                tokens.map(t => t ++ buildNextNGrams(t, n)).map(countWords)

            log("Building Dictionary")
            val dictionary =
                buildDictionary(countsByDocuments).filter(
                    w => w.token.words.forall(_.length > minCharacters)
                        && w.tf >= minTf && w.df >= minDf)

            log("Saving dictionary")
            dictionary.save(s"${name}.dict-${n}.csv")

            val tokensWithLargerNGrams =
                tokens.map(tokenizeWithoutConstituentTokens(_, dictionary.map.contains, n))

            if (n == maxN) (tokensWithLargerNGrams, dictionary)
            else rec(tokensWithLargerNGrams, n + 1)
        }

        log("Extracting words")
        val words = documents.map(tokenizeBySpace(_).map(NGram(_)))

        val (tokens, dictionary) = rec(words, 2)
        (tokens.map(countWords), dictionary)
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