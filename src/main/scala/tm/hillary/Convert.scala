package tm.hillary

import java.io.FileReader
import org.apache.commons.csv.CSVFormat
import java.util.Date
import java.text.SimpleDateFormat
import scala.collection.mutable
import scala.collection.GenSeq
import tm.text.Preprocessor
import tm.text.DataConverter
import scala.collection.JavaConversions._
import java.io.InputStream
import java.io.InputStreamReader
import java.io.FileInputStream
import tm.text.Document
import tm.text.Sentence
import java.nio.file.Paths
import org.slf4j.LoggerFactory

object Convert {
  val logger = LoggerFactory.getLogger(this.getClass)
  
  def main(args: Array[String]) {
    if (args.length < 2)
      printUsage()
    else {
      import Parameters.implicits.settings

      tm.text.Convert.main(args)
    }
  }

  def printUsage() = {
    println("tm.hillary.Convert name source_directory maxWords")
  }

  def run() = {
    import Parameters.implicits.settings

    logger.info("Extracting bodies")
    implicit val asciiOnly = settings.asciiOnly
    val bodies = Emails.readEmailsFromDefaultPath.map{email =>
      //Was changed during Jan 2018 update
      //Not yet tested
      val cleanText = Preprocessor.preprocess(email.content)
      val tokens = Preprocessor.tokenizeBySpace(cleanText)
      Document(Sentence(tokens))
    }.toList.par

    DataConverter("hillary", bodies)
  }
}
