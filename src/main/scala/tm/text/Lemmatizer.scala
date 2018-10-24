package tm.text

import scala.io.Source
import java.io.InputStream
import java.io.FileInputStream
import scalaz._, Scalaz._

/**
 * Lemmatization: to find the root form
 * apples -> apple 
 * regularization -> regular
 * success -> success
 * gone -> go
 * 
 * Stemming: to remove prefixes and suffixes
 * apples -> apple
 * regularization -> regulariza
 * success -> succe
 * gone -> gone
 * 
 */
object DictionaryLemmatizer {
  def read(filename: String)(enc: String = "UTF8")(f: String => Map[String, String]): DictionaryLemmatizer = {
    readFromStream(new FileInputStream(filename))(enc)(f)
  }

  def readFromStream(input: InputStream)(enc: String = "UTF8")(f: String => Map[String, String]): DictionaryLemmatizer = {
    new DictionaryLemmatizer(
      Source.fromInputStream(input)(enc).getLines.map(f).reduce{(mapA, mapB) => mapA |+| mapB})
  }
  
  lazy val EnglishLemmatizer = readFromStream(this.getClass.getResourceAsStream("/tm/text/lemmatization-en.txt"))("UTF-8")((line) => Map(line.split('\t')(1) -> line.split('\t')(0)))
  
  lazy val Empty = new DictionaryLemmatizer(Map.empty[String, String])
}

class DictionaryLemmatizer(dictionary: Map[String, String]) {
  def lemmatize(word: String) = dictionary.getOrElse(word, word)
}
