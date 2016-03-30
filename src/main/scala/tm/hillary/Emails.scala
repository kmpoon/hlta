package tm.hillary

import java.io.FileInputStream
import java.util.Date
import java.io.InputStream
import java.text.SimpleDateFormat
import java.io.InputStreamReader
import org.apache.commons.csv.CSVFormat
import tm.text.Preprocessor
import scala.collection.JavaConversions._
import tm.text.StanfordLemmatizer
import tm.text.Sentence
import scala.util.matching.Regex.Match
import scala.util.matching.Regex
import tm.text.StopWords
import tm.text.Document

case class Email(id: Int, date: Option[Date], subject: String, body: String) {
  def content = subject + "\n" + body
}

object Emails {

  val minChars = 3

  def readEmailsFromDefaultPath() =
    readEmails(new FileInputStream("data/Emails.csv"))

  def readEmails(inputStream: InputStream): Iterable[Email] = {
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

      Email(id, date, r.get("ExtractedSubject"), r.get("ExtractedBodyText"))
    }
  }
}