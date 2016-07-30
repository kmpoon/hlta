package tm.text

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter.Settings
import java.io.PrintWriter
import java.nio.file.Paths
import java.nio.file.Path
import org.slf4j.LoggerFactory

object Convert {
  val logger = LoggerFactory.getLogger(Convert.getClass)

  def convert(name: String, source: Path)(implicit settings: Settings) = {
    import Preprocessor.tokenizeBySpace

    logger.info("Reading documents")
    val paths = getFiles(source)

    val outputs = paths.par.map { p =>
      // each line is assumed to be a sentence containing tokens
      // separated by space
      val source = Source.fromFile(p.toFile)
      try {
        logger.debug("Reading {}", p.toFile)
        val sentences = source.getLines
          .map(tokenizeBySpace)
          .map(ts => new Sentence(ts.map(NGram(_))))
        (p, new Document(sentences.toList))
      } catch {
        case e: Exception =>
          logger.error("Unable to read file: " + p.toFile, e)
          throw e
      } finally {
        source.close
      }
    }

    val (readFiles, documents) = outputs.unzip

    logger.info("Saving file names")
    val writer = new PrintWriter(s"${name}.files.txt")
    writer.println(readFiles.mkString("\n"))
    writer.close

    DataConverter.convert(name, documents)
  }

  def getFiles(source: Path) =
    FileHelpers.findFiles(source, "txt").map(source.resolve)

}