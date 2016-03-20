package tm.pdf

import tm.text.DataConverter
import scala.collection.JavaConversions._
import java.io.File
import scala.io.Source
import tm.util.FileHelpers

object Convert {
    def main(args: Array[String]) {
        if (args.length < 1)
            printUsage()
        else
            convert(args(0))

    }

    def printUsage() = {
        println("tm.pdf.Convert source_directory")
    }

    def convert(source: String) = {
        val log = println(_: String)

        log("Read documents")
        val files = getFiles(source)
        val bodies = files
            .map(f => Source.fromFile(f).getLines.mkString("\n"))
            .toList.par

        implicit val parameters =
            DataConverter.implicits.default.copy(
                maxN = 3, minDf = files.length / 100)
        DataConverter.convert("aaai", bodies, log)
    }

    def getFiles(source: String) = {
        new File(source).list
            .filter(_.endsWith(".txt"))
            .map(f => FileHelpers.getPath(source, f))
    }
}