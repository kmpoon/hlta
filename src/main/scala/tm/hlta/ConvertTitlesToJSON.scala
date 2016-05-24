package tm.hlta

import scala.io.Source
import java.io.PrintWriter
import scala.language.higherKinds
import scala.collection.generic.CanBuildFrom

object TitleFile {
  case class Document(title: String, conference: String, year: Int)

  val pathRegex = """(?:(?:.*?)/)?(aaai|ijcai)-(\d+)/(?:(?:.*?)/)?([^/]*)\.txt""".r

  def readDocuments(lines: Iterator[String]) = {
    lines.map(
      _ match {
        case pathRegex(conference, year, title) =>
          Document(title, conference, year.toInt)
      })
  }

  def readDocuments(titleFile: String): Iterator[Document] =
    readDocuments(Source.fromFile(titleFile).getLines)

  def writeDocumentsAsJSON(
    documents: TraversableOnce[Document], outputFile: String) = {
    val writer = new PrintWriter(outputFile)
    writer.println("var documents = [")

    writer.println(documents.map(d =>
      s"""  { title: '${d.title.replaceAll("'", "\\\\'")}', source: "${d.conference}", year: "${d.year}" }""")
      .mkString(",\n"))

    writer.println("];")
    writer.close
  }
}

object ConvertTitlesToJSON {
  def main(args: Array[String]) {
    if (args.length < 2)
      printUsage
    else
      run(args(0), args(1))
  }

  def printUsage() = {
    println("ConvertTitlesToJSON titles_file output_name")
    println
    println("E.g. ConvertTitlesToJSON test.files.txt test")
    println("The output file will be named test.documents.js")
  }

  def run(titleFile: String, outputName: String) = {
    import TitleFile._

    val documents = readDocuments(titleFile)
    writeDocumentsAsJSON(documents, outputName + ".titles.js")
  }
}