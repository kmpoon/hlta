package tm.text

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter.Settings
import java.io.PrintWriter
import java.nio.file.Paths
import java.nio.file.Path

object Convert {
  def convert(name: String, source: Path)(implicit settings: Settings) = {
    import Preprocessor.tokenizeBySpace
    import StopWords.implicits.default

    val log = println(_: String)

    log("Reading documents")
    val paths = getFiles(source).toList

    val outputs = paths.par.map { p =>
      // each line is assumed to be a sentence containing tokens
      // separated by space
      val source = Source.fromFile(p.toFile)
      try {
        val sentences = source.getLines
          .map(tokenizeBySpace)
          .map(ts => new Sentence(ts.map(NGram(_))))
        (p, new Document(sentences.toList))
      } finally {
        source.close
      }
    }

    val (readFiles, documents) = outputs.unzip

    log("Saving file names")
    val writer = new PrintWriter(s"${name}.files.txt")
    writer.println(readFiles.mkString("\n"))
    writer.close

    DataConverter.convert(name, documents, log)
  }

  def getFiles(source: Path) =
    FileHelpers.findFiles(source, "txt").map(source.resolve)

}