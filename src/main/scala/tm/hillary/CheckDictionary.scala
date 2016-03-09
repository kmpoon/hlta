package tm.hillary

import tm.text.Dictionary

object CheckDictionary extends App {
    println("Welcome to the Scala worksheet")
    val dictionary = Dictionary.read(
        "/Users/kmpoon/Documents/research/workspace/HillaryEmails/" +
            "converted/hillary.20160226.dict.csv")
    dictionary.info.filter(w => w.word.endsWith("nt") && w.word.length <= 8).foreach(println)
}