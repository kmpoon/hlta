package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream

object StopWords {//TODO: update reader encoding option
  def read(filename: String)(enc: String): StopWords = {
    read(new FileInputStream(filename))(enc)
  }

  def read(input: InputStream)(enc: String): StopWords = {
    new StopWords(
      Source.fromInputStream(input)(enc).getLines.filter(_.size > 0).toSet)
  }

  //shall be removed in the future
  //alternative StopWords.EnglishStopwords()
  @Deprecated
  object implicits {
    implicit val default = EnglishStopwords()
  }
  
  def EnglishStopwords() = StopWords.read(this.getClass.getResourceAsStream("/tm/text/stopwords-lewis.csv"))("UTF-8")
  
  def Empty() = new StopWords(Set.empty[String])
}

class StopWords(words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
