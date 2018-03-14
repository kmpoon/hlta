package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream

object StopWords {//TODO: update reader encoding option
  def read(filename: String): StopWords = {
    read(new FileInputStream(filename))
  }

  def read(input: InputStream): StopWords = {
    new StopWords(
      Source.fromInputStream(input).getLines.filter(_.size > 0).toSet)
  }

  //shall be removed in the future
  //alternative StopWords.EnglishStopwords()
  @Deprecated
  object implicits {
    implicit val default = EnglishStopwords()
  }
  
  val EnglishStopwordsFile = "/tm/text/stopwords-lewis.csv"
  
  def EnglishStopwords() = StopWords.read(this.getClass.getResourceAsStream(EnglishStopwordsFile))
  
  def Empty() = new StopWords(Set.empty[String])
}

class StopWords(words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
