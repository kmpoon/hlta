package tm.pdf

import tm.text.Convert
import tm.text.WordSelector

import org.rogach.scallop._
import tm.util.Arguments

object Parameters {
  object implicits {
    val defaultMinDf = 0.0
    val defaultMaxDf = 0.25
    val defaultMaxWords = 5000
    val asciiOnly = true

    implicit val settings =
      Convert.Settings(concatenations = 3, minCharacters = 3,
        wordSelector = WordSelector.ByTfIdf(
          3, defaultMinDf, defaultMaxDf, defaultMaxWords), asciiOnly = asciiOnly)
  }

  class Conf(args: Seq[String]) extends Arguments(args) {
    val s = implicits.settings
    val concatenations = opt[Int](short = 'n',
//      default = Some(s.maxN),
      descr = "number of concatenations used for building n-grams",
      required = true)
    val minChars = opt[Int](noshort = true,
      default = Some(s.minCharacters),
      descr = "minimum length of selected words")
    val minDf = opt[Double](noshort = true,
      default = Some(implicits.defaultMinDf),
      descr = "minimum document fraction of selected words")
    val maxDf = opt[Double](noshort = true,
      default = Some(implicits.defaultMaxDf),
      descr = "maximum document fraction of selected words")
    val maxWords = opt[Int](short = 'w',
//      default = Some(implicits.defaultMaxWords),
      descr = "maximum of selected words",
      required = true)
    val asciiOnly = opt[Boolean](default = Some(true), descr = "aceept ASCII only, default true")

    def getSettings() = {
      Convert.Settings(concatenations = concatenations(),
        minCharacters = minChars(),
        wordSelector = WordSelector.ByTfIdf(minChars(), minDf(), maxDf(), maxWords()),
        asciiOnly = asciiOnly())
    }
  }

}