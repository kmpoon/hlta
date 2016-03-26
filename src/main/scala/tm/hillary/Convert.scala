package tm.hillary

import java.io.FileReader
import org.apache.commons.csv.CSVFormat
import java.util.Date
import java.text.SimpleDateFormat
import scala.collection.mutable
import scala.collection.GenSeq
import tm.text.Preprocessor
import tm.text.StopWords
import tm.text.DataConverter
import scala.collection.JavaConversions._
import java.io.InputStream
import java.io.InputStreamReader
import java.io.FileInputStream
import tm.text.Document

object Convert {
    def main(args: Array[String]) {
        run(println)
    }

    def run(log: (String) => Any) = {
        import DataConverter.implicits.default

        log("Extracting bodies")
        val bodies = readEmailsFromDefaultPath
            .map(email => Document(email._3)).toList.par

        DataConverter.convert("hillary", bodies, log)
    }

    def preprocess(subject: String, body: String) =
        Preprocessor.preprocess(subject + "\n" + body)

    def readEmailsFromDefaultPath() =
        readEmails(new FileInputStream("data/Emails.csv"))

    def readEmails(inputStream: InputStream): Iterable[(Int, Option[Date], String)] = {
        // must be put here since it may not be thread-safe
        val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")

        val records = CSVFormat.EXCEL.withHeader()
            .parse(new InputStreamReader(inputStream));
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
}
