

package hillary

import java.io.FileReader
import org.apache.commons.csv.CSVFormat
import java.util.Date
import java.text.SimpleDateFormat
import scala.collection.mutable
import scala.collection.GenSeq

object Convert extends App {
    run(println)

    def run(log: (String) => Any) = {
        import Converter._

        val maxN = 2
        val minTf = 6

        log("Extracting bodies")
        val bodies = readEmails.map(_._3).toList.par

        log("Extracting words")
        val wordsByEmails = bodies.map(tokenizeBySpace)

        log("Counting words in each email")
        val wordCountsByEmails = wordsByEmails
            .map(find1ToNGrams(_, maxN).flatten)
            .map(countWords)

        log("Building Dictionary")
        val dictionary = buildDictionary(wordCountsByEmails).filter(_.tf >= minTf)

        log("Saving dictionary")
        dictionary.save("dictionary.csv")

        val tokenCountsByEmails = wordsByEmails
            .map(words =>
                tokenizeWithoutConstituentTokens(words, dictionary.map.contains, 2))
            .map(countWords)

        log("Converting to bow")
        val bow = convertToBow(tokenCountsByEmails, dictionary.map)

        log("Saving in ARFF format")
        saveAsArff("hillary", "bow.arff",
            AttributeType.numeric, dictionary.words, bow.seq)
        saveAsBinaryHlcm("hillary", "bow.txt", dictionary.words, bow.seq)

        log("done")
    }
}
