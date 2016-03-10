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
        import DataConverter.implicits.default

        log("Read documents")
        val bodies = getFiles(source)
            .map(f => Source.fromFile(f).getLines.mkString("\n"))
            .toList.par

        DataConverter.convert("aaai", bodies, log)
    }

    def getFiles(source: String) = {
        new File(source).list
            .filter(_.endsWith(".txt"))
            .map(f => FileHelpers.getPath(source, f))
    }
}