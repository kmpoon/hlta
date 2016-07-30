package tm.pdf

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter
import java.nio.file.Paths
import tm.text.WordSelector

import org.rogach.scallop._
import tm.util.Arguments

object Convert {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("Usage: tm.pdf.Convert [OPTION]... name max-words max-n source")
    val name = trailArg[String](descr = "Name of data")
    val maxWords = trailArg[Int](descr = "Maximum number of words")
    val maxN = trailArg[Int](descr = "Maximum n of n-gram")
    val source = trailArg[String](descr = "Source directory")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    implicit val settings =
      DataConverter.Settings(maxN = conf.maxN(), minCharacters = 3,
        selectWords = WordSelector.byTfIdf(3, 0, .25, conf.maxWords()))

    tm.text.Convert.convert(conf.name(), Paths.get(conf.source()))

  }
}