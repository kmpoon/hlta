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

object Convert extends App {
    run(println)

    def run(log: (String) => Any) = {
        import DataConverter.implicits.default

        log("Extracting bodies")
        val bodies = readEmails.map(_._3).toList.par
        
        DataConverter.convert("hillary", bodies, log)
    }

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
}
