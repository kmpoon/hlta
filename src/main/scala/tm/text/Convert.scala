package tm.text

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter.Settings

object Convert {
    def convert(name: String, source: String)(implicit settings: Settings) = {
        import Preprocessor.tokenizeBySpace
        import StopWords.implicits.default

        val log = println(_: String)

        log("Reading documents")
        val files = getFiles(source)

        val documents = files.toList.par.map { f =>
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

        DataConverter.convert(name, documents, log)
    }

    def getFiles(source: String) = {
        new File(source).list
            .filter(_.endsWith(".txt"))
            .map(f => FileHelpers.getPath(source, f))
    }
}