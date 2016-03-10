package tm.hillary

import java.io.FileReader
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.GenSeq
import scala.collection.JavaConversions.iterableAsScalaIterable

import org.apache.commons.csv.CSVFormat

import tm.text.Preprocessor
import tm.text.StopWords

object Converter {

    object AttributeType extends Enumeration {
        val binary, numeric = Value
    }

    type WordCounts = Map[String, Int]

    val stopWords = StopWords.read("stopwords-lewis.csv")

    def preprocess(subject: String, body: String) =
        Preprocessor.preprocess(subject + "\n" + body)

    def readEmails(): Iterable[(Int, Option[Date], String)] = {
        val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        val in = new FileReader("data/Emails.csv");
        val records = CSVFormat.EXCEL.withHeader().parse(in);
        records.view.map { r =>
            val id = r.get("Id").toInt
            val date = r.get("MetadataDateSent") match {
                case "" => None
                case d => Some(df.parse(d))
            }
            val text = preprocess(
                r.get("ExtractedSubject"), r.get("ExtractedBodyText"))
            (id, date, text)
        }
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
