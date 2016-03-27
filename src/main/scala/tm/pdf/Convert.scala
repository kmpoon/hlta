package tm.pdf

import tm.text.DataConverter
import scala.collection.JavaConversions._
import java.io.File
import scala.io.Source
import tm.util.FileHelpers
import tm.text.Preprocessor
import tm.text.StopWords
import tm.text.Sentence
import tm.text.Document
import tm.text.NGram

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
        import Preprocessor.tokenizeBySpace
        import StopWords.implicits.default

        val log = println(_: String)

        log("Reading documents")
        val files = getFiles(source)
        val bodies = files.toList.par.map { f =>
            // each line is assumed to be a sentence containing tokens
            // separated by space
            val source = Source.fromFile(f)
            try {
                val sentences = source.getLines
                    .map(tokenizeBySpace)
                    .map(ts => new Sentence(ts.map(NGram(_))))
                new Document(sentences.toList)
            } finally {
                source.close
            }
        }

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