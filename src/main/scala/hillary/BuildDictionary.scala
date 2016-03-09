package hillary

object BuildDictionary extends App {
    run()

    def run() = {
        import Converter._

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