package tm.hillary

import tm.text.Preprocessor
import tm.text.StopWords

object BuildDictionary extends App {
    run()

    def run() = {
        import Preprocessor._
        import StopWords.implicits._

        println("Extracting bodies")
        val bodies = Convert.readEmailsFromDefaultPath.map(_._3).toList.par

        println("Counting words in each email")
        val countsByEmails = bodies.map(tokenizeAndCount(_, 3)).par

        println("Building Dictionary")
        val dictionary = buildDictionary(countsByEmails)

        println("Saving dictionary")
        dictionary.save("dictionary.all.csv")

        println("done")
    }

}