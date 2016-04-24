package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream

object StopWords {
  def read(filename: String): StopWords = {
    read(new FileInputStream(filename))
  }

  def read(input: InputStream): StopWords = {
    new StopWords(
      Source.fromInputStream(input).getLines.filter(_.size > 0).toSet)
  }

  object implicits {
    implicit val default = StopWords.read(
      this.getClass.getResourceAsStream("/tm/text/stopwords-lewis.csv"))
  }
}

class StopWords(words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
