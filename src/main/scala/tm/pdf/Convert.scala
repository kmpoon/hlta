package tm.pdf

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter
import java.nio.file.Paths
import tm.text.WordSelector

object Convert {
  def main(args: Array[String]) {
    if (args.length < 4)
      printUsage()
    else {
      val maxWords = args(1).toInt
      val maxN = args(2).toInt
      implicit val settings =
        DataConverter.Settings(maxN = maxN, minCharacters = 3,
          selectWords = WordSelector.byTfIdf(3, 0, .25, maxWords))

      tm.text.Convert.convert(args(0), Paths.get(args(3)))
    }
  }

  def printUsage() = {
    println("tm.pdf.Convert name max_number_of_words n_of_n_gram source_directory")
  }
}