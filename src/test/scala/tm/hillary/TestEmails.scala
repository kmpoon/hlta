package tm.hillary

import tm.text.Preprocessor
import tm.text.StopWords
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

trait TestEmails {
  import StopWords.implicits.default
  //import Parameters.implicits.settings

  val emails = Emails.readEmails(
    new GZIPInputStream(getClass.getResourceAsStream("/Emails.csv.gz")))

  def bodies = emails
    .map(ExtractText.preprocess)
    .map(ExtractText.documentToString)

  def countWordsInEmails(numberOfEmails: Int, n: Int = 1) =
    bodies.take(numberOfEmails).toList
      .map(Preprocessor.tokenizeAndCount(_, 1))
}
