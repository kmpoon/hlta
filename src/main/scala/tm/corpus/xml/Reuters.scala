package tm.corpus.xml

import tm.corpus.Extractor
import scala.xml.XML
import java.nio.file.Path
import scala.io.Source
import tm.util.manage
import scala.xml.Elem

object ExtractReuters extends Extractor {
  val extension = "sgm"
  val ReuterRegex = """(?s)<REUTERS(?:.*?) NEWID="(\d+)">(?:.*?)(<TEXT(?:.*?)>(?:.*?)</TEXT>)\s*</REUTERS>""".r
  val encoding = "ISO-8859-1"

  def main(args: Array[String]) {
    run(args)
  }

  /**
   * Extract the content in the <text> element.
   */
  override def extractText(inputDir: Path, inputFile: Path): Seq[(String, String)] = {
    logger.info("Extracting text from file: {}", inputFile.toString)

    val content =
      manage(Source.fromFile(inputDir.resolve(inputFile).toFile)(encoding)) {
        _.getLines().mkString("\n")
      }

    val pairs = (for (r <- ReuterRegex.findAllMatchIn(content)) yield {
      val id = f"${r.group(1).toInt}%05d"
      val content = try {
        //        logger.info("Reading NEWID: {}", id)
        // needs to remove some unknown entities e.g. &#3;
        val text = XML.loadString(r.group(2).replaceAll("&(.*?);", " "))
        getText(id, text)
      } catch {
        case ex: Exception =>
          logger.error(ex.getMessage)
          logger.error(r.group(2))
          None

      }
      (id, content)
    }).toSeq

    val failure = pairs.filter(_._2.isEmpty)
    if (failure.size > 0)
      logger.error(
        "Cannot extract text from the articles with these NEWID: " +
          failure.map(_._1).mkString(", "))

    pairs.collect({
      case (id, Some(content)) => (s"$id.txt", content)
    })
  }

  def getText(id: String, text: Elem): Option[String] = {
    def getChild(child: String) = {
      val children = text \ child
      if (children.isEmpty) {
        logger.info(s"Article with NEWID ${id}: Cannot find the child ${child}.")
        ""
      } else
        children.text
    }

    //    text.attribute("TYPE").map(_.text) match {
    //      case None =>
    //        Some(getChild("TITLE") + ".\n\n" + getChild("BODY"))
    //      case Some("BRIEF") =>
    //        Some(getChild("TITLE"))
    //      case Some("UNPROC") =>
    //        Some(text.text)
    //      case Some(t) =>
    //        logger.error(s"Article with NEWID ${id}: Unkown TEXT TYPE: ${t}.")
    //        None
    //    }

    text.attribute("TYPE").map(_.text) match {
      case None =>
        Some(getChild("TITLE") + ".\n\n" + getChild("BODY"))
      case Some("BRIEF") =>
        None
      case Some("UNPROC") =>
        None
      case Some(t) =>
        logger.error(s"Article with NEWID ${id}: Unkown TEXT TYPE: ${t}.")
        None
    }
  }

}