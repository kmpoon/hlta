package tm.hillary

import tm.text.Preprocessor

trait Emails {
    val emails = Converter.readEmails()
    def bodies = emails.map(_._3)

    def countWordsInEmails(numberOfEmails: Int, n: Int = 1) =
        bodies.take(numberOfEmails).toList.map(Preprocessor.tokenizeAndCount(_, 1))
}
