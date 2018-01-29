package tm.corpus.xml

import tm.corpus.Extractor
import java.nio.file.Path
import scala.xml.XML
import scala.util.matching.Regex.Match

/**
 * For extracting text from the JRC-Acquis corpus.
 */
object ExtractJRCAcquis extends Extractor {
  val extension = "xml"

  def main(args: Array[String]) {
    run(args)
  }

  /**
   * Extract the content of the <text>/<body>/<div type="body"> element.
   */
  override def extractSingleText(inputFile: Path): String = {
    val xml = XML.loadFile(inputFile.toFile)
    val body = xml \ "text" \ "body"

    val content = body.flatMap(_.child)
      .filter(_.attribute("type").map(_.text) == Some("body"))
      .text
    content.replaceAll("%quot%", "")
  }
}
