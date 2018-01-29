package tm.corpus.aan

import tm.corpus.Extractor
import java.nio.file.Path
import tm.util.FileHelpers
import tm.util.manage
import scala.io.Source
import java.nio.file.Files

object ExtractText extends Extractor {
  val minFileSize = 500
  val extension: String = "txt"
  val FilenameRegex = raw"([A-z](?:\d+)-(?:\d+))(?:[A-z]+)?\.txt".r

  def main(args: Array[String]) {
    run(args)
  }

  override def findFiles(inputDir: Path) = {
    def hasValidSize(p: Path) = Files.size(inputDir.resolve(p)) >= minFileSize

    val files = FileHelpers.findFiles(inputDir, extension)
      .filter(hasValidSize).sortBy(_.toString())

    // remove duplicates
    files.foldLeft((List.empty[Path], Option.empty[String])) { (pair, path) =>
      path.getFileName.toString() match {
        case FilenameRegex(n) =>
          val name = Some(n)
          if (pair._2 != name)
            (path +: pair._1, name)
          else {
            logger.info("File is not selected since it looks like a duplicate: {}", path)
            pair
          }
        case _ => {
          logger.info("File is not selected since its name does not match the expected pattern: {}", path.getFileName)
          pair
        }
      }
    }._1.reverse.toVector
  }

  override def extractSingleText(inputFile: Path): String =
    manage(Source.fromFile(inputFile.toFile)) { _.getLines.mkString("\n") }
}