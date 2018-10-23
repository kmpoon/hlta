package tm.hillary

import tm.text.Dictionary

object CheckDictionary extends App {
  val dictionary = Dictionary.read(
    "/Users/kmpoon/Documents/research/experiments/hlta/20160307-pdf/converted/" +
      "aaai-ijcai.20160326.dict-3.csv")
  val filtered = dictionary.info.filter(
    w => w.token.words.find(w =>
      !w.matches("[\\p{Alpha}_-]+")).isDefined)
  filtered.foreach(println)
}