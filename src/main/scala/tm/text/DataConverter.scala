package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter

object DataConverter {
    object AttributeType extends Enumeration {
        val binary, numeric = Value
    }

    case class Settings(val maxN: Int, val minTf: Int)

    object implicits {
        implicit val default = new Settings(maxN = 2, minTf = 6)
    }

    type WordCounts = Map[String, Int]

    def convert(name: String, documents: GenSeq[String], log: (String) => Any)(implicit settings: Settings) = {
        import Preprocessor._
        import settings._
        import StopWords.implicits.default

        log("Extracting words")
        val wordsByDocuments = documents.map(tokenizeBySpace)

        log("Counting words in each email")
        val wordCountsByEmails = wordsByDocuments
            .map(find1ToNGrams(_, maxN).flatten)
            .map(countWords)

        log("Building Dictionary")
        val dictionary = buildDictionary(wordCountsByEmails)
            .filter(_.tf >= minTf)

        log("Saving dictionary")
        dictionary.save(s"${name}.dict.csv")

        val tokenCountsByEmails = wordsByDocuments
            .map(words =>
                tokenizeWithoutConstituentTokens(words, dictionary.map.contains, 2))
            .map(countWords)

        log("Converting to bow")
        val bow = convertToBow(tokenCountsByEmails, dictionary.map)

        log("Saving in ARFF format")
        saveAsArff(name, s"${name}.arff",
            AttributeType.numeric, dictionary.words, bow.seq)
        saveAsBinaryHlcm(name, s"${name}.txt", dictionary.words, bow.seq)

        log("done")
    }

    /**
     * Converts data to bag-of-words representation, based on the given word
     * counts and dictionary.
     */
    def convertToBow(countsByDocuments: GenSeq[WordCounts], indices: Map[String, Int]) = {
        def convert(counts: WordCounts) = {
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