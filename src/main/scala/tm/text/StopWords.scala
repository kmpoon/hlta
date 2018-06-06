package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream

object StopWords {
  def read(filename: String)(enc: String = "UTF8"): StopWords = {
    readFromStream(new FileInputStream(filename))(enc)
  }

  def readFromStream(input: InputStream)(enc: String = "UTF8"): StopWords = {
    new StopWords(
      Source.fromInputStream(input)(enc).getLines.filter(_.size > 0).toSet)
  }

  //shall be removed in the future
  //alternative StopWords.EnglishStopwords()
  @Deprecated
  object implicits {
    implicit val default = EnglishStopwords()
  }
  
  def EnglishStopwords() = StopWords.readFromStream(this.getClass.getResourceAsStream("/tm/text/stopwords-lewis.csv"))("UTF-8")
  
  def ChineseStopwords() = StopWords.readFromStream(this.getClass.getResourceAsStream("/tm/text/chinese-stopwords-list.txt"))("UTF-8")
  
  def Empty() = new StopWords(Set.empty[String])
}

class StopWords(words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
