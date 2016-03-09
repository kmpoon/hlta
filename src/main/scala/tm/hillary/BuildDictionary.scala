package tm.hillary

import tm.text.Preprocessor

object BuildDictionary extends App {
    run()

    def run() = {
        import Converter._
        import Preprocessor._

        println("Extracting bodies")
        val bodies = readEmails.map(_._3).toList.par

        println("Counting words in each email")
        val countsByEmails = bodies.map(tokenizeAndCount(_, 3)).par

        println("Building Dictionary")
        val dictionary = buildDictionary(countsByEmails)

        println("Saving dictionary")
        dictionary.save("dictionary.all.csv")

        println("done")
    }

}