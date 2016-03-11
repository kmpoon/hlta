package tm.hillary

import tm.text.Preprocessor
import tm.text.StopWords
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

trait Emails {
    val emails = Convert.readEmails(
        new GZIPInputStream(getClass.getResourceAsStream("/Emails.csv.gz")))
    def bodies = emails.map(_._3)

    def countWordsInEmails(numberOfEmails: Int, n: Int = 1)(implicit stopWords: StopWords) =
        bodies.take(numberOfEmails).toList.map(Preprocessor.tokenizeAndCount(_, 1))
}
