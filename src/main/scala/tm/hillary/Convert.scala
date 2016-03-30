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
    if (args.length < 2)
      printUsage()
    else {
      import Parameters.implicits.settings

      tm.text.Convert.convert(args(0), args(1))
    }
  }

  def printUsage() = {
    println("tm.hillary.Convert name source_directory")
  }

  def run(log: (String) => Any) = {
    import Parameters.implicits.settings

    log("Extracting bodies")
    val bodies = Emails.readEmailsFromDefaultPath
      .map(email => Document(email.content)).toList.par

    DataConverter.convert("hillary", bodies, log)
  }
}
