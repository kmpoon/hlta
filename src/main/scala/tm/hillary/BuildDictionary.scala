package tm.hillary

import tm.text.Preprocessor
import tm.text.StopWords

object BuildDictionary extends App {
  run()

  def run() = {
    import Preprocessor._
    import StopWords.implicits._

    println("Extracting bodies")
    val bodies = Emails.readEmailsFromDefaultPath.map(_.content).toList.par

    println("Counting words in each email")
    val countsByEmails = bodies.map(tokenizeAndCount(_, 3)).par

    println("Building Dictionary")
    val dictionary = buildDictionary(countsByEmails)

    println("Saving dictionary")
    dictionary.save("dictionary.all.csv")

    println("done")
  }

}