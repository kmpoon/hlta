package tm.text

import scala.io.Source

object StopWords {
  def read(filename: String): StopWords = {
    new StopWords(
      Source.fromFile(filename).getLines.filter(_.size > 0).toSet)
  }

  object implicits {
    implicit val default = StopWords.read("stopwords-lewis.csv")
  }
}

class StopWords(words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
