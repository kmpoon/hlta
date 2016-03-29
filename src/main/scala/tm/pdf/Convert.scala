package tm.pdf

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter

object Convert {
    def main(args: Array[String]) {
        if (args.length < 2)
            printUsage()
        else {
            import Parameters.implicits.settings

            tm.text.Convert.convert(args(0), args(1))
        }

    }

    def printUsage() = {
        println("tm.pdf.Convert name source_directory")
    }

}