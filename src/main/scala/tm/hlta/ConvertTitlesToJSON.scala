package tm.hlta

import scala.io.Source
import java.io.PrintWriter

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

  case class Document(title: String, conference: String, year: Int)

  val pathRegex = """(?:(?:.*?)/)?(aaai|ijcai)-(\d+)/(?:(?:.*?)/)?([^/]*)\.txt""".r

  def run(titleFile: String, outputName: String) = {
    val documents = readDocuments(titleFile)
    writeDocuments(documents, outputName + ".titles.js")
  }

  def readDocuments(titleFile: String) =
    Source.fromFile(titleFile).getLines.map {
      _ match {
        case pathRegex(conference, year, title) =>
          Document(title, conference, year.toInt)
      }
    }

  def writeDocuments(documents: Iterator[Document], outputFile: String) = {
    val writer = new PrintWriter(outputFile)
    writer.println("var documents = [")

    writer.println(documents.map(d =>
      s"""  { title: '${d.title.replaceAll("'", "\\\\'")}', source: "${d.conference}", year: "${d.year}" }""")
      .mkString(",\n"))

    writer.println("];")
    writer.close
  }
}