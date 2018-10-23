package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream

object StopWords {
  def read(filename: String)(implicit enc: String = "UTF8"): StopWords = {
    readFromStream(new FileInputStream(filename))(enc)
  }

  def readFromStream(input: InputStream)(implicit enc: String = "UTF8"): StopWords = {
    new StopWords(
      Source.fromInputStream(input)(enc).getLines.filter(_.size > 0).toSet)
  }

  //shall be removed in the future
  //alternative StopWords.EnglishStopwords()
  @Deprecated
  object implicits {
    implicit val default = EnglishStopwords
  }
  
  lazy val EnglishStopwords = readFromStream(this.getClass.getResourceAsStream("/tm/text/stopwords-lewis.csv"))("UTF-8")
  
  lazy val ChineseStopwords = readFromStream(this.getClass.getResourceAsStream("/tm/text/chinese-stopwords-list.txt"))("UTF-8")
  
  val Empty = new StopWords(Set.empty[String])
}

class StopWords(val words: Set[String]) {
  def contains(word: String) = words.contains(word)
}
