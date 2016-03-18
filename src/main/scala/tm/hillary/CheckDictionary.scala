package tm.hillary

import tm.text.Dictionary

object CheckDictionary extends App {
    println("Welcome to the Scala worksheet")
    val dictionary = Dictionary.read(
        "/Users/kmpoon/Documents/research/workspace/HillaryEmails/" +
            "converted/hillary.20160226.dict.csv")
    dictionary.info.filter(
        w => w.token.words.head.endsWith("nt") && w.token.words.length <= 8).foreach(println)
}