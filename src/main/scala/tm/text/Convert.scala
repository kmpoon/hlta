package tm.text

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import tm.text.DataConverter.Settings
import java.io.PrintWriter
import java.nio.file.Paths
import java.nio.file.Path
import org.slf4j.LoggerFactory
import scala.collection.GenMap
import scala.collection.GenSeq
import tm.util.ParMapReduce._

import org.rogach.scallop._
import tm.util.Arguments

object Convert {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("Usage: tm.text.Convert [OPTION]... name max-words concatenations source")
    val minCharacters = opt[Int](
      default = Some(3),
      descr = "Minimum number of characters of a word to be selected. Default: 3")
    val minDocFraction = opt[Double](
      default = Some(0.0),
      descr = "Minimum fraction of documents that a token can appear to be selected. Default: 0.0")
    val maxDocFraction = opt[Double](
      default = Some(0.25),
      descr = "Maximum fraction of documents that a token can appear to be selected. Default: 0.25")
    val seedFile = opt[String](descr = "File containing tokens to be included, regardless of other selection criteria.")
    val seedNumber = opt[Int](descr = "Number of seed tokens to be included. Need to be specified when seed file is given.")
    val name = trailArg[String](descr = "Name of data")
    val maxWords = trailArg[Int](descr = "Maximum number of words")
    val concatenations =
      trailArg[Int](descr = "Number of concatentations for building n-grams")
    val source = trailArg[String](descr = "Source directory")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val seedFile = conf.seedFile.toOption.map(s => new SeedTokens(s))
    for (sf <- seedFile) {
      if (sf.max > 2) throw new IllegalArgumentException(
        s"The file ${sf.file} contains n-grams with n = ${sf.max}. Currently only n = 2 is supported.")

      logger.info("Using seed tokens from file: {}", sf.file)
    }

    implicit val settings =
      DataConverter.Settings(
        concatenations = conf.concatenations(),
        minCharacters = conf.minCharacters(),
        wordSelector = WordSelector.byTfIdf(
          conf.minCharacters(), conf.minDocFraction(),
          conf.maxDocFraction()))

    convert(conf.name(), Paths.get(conf.source()), conf.maxWords(), seedFile)
  }

  val logger = LoggerFactory.getLogger(Convert.getClass)

  type Cache = Map[String, NGram]

  /**
   * Represents a file containing tokens to be selected.
   */
  class SeedTokens(val file: String) {
    val tokens = NGram.readFile(file)
    private lazy val set = tokens.toSet

    lazy val max =
      if (tokens.length > 0) tokens.map(_.words.size).max else 0

    def contains(ngram: NGram) = set.contains(ngram)
  }

  def convert(name: String, source: Path, maxWords: Int, seeds: Option[SeedTokens])(
    implicit settings: Settings) = {
    val documents = readFiles(Some(name), source)
    DataConverter.convert(name, documents, maxWords, seeds)
  }

  def buildCache(paths: Vector[Path]): Cache = {
    import Preprocessor.tokenizeBySpace

    logger.info("Building cache")
    mapReduce(paths.par)(readFile { _.flatten.toSet })(_ ++ _)
      .map(t => t -> NGram(t)).toMap
  }

  def readFile[T](f: (Seq[Seq[String]]) => T)(p: Path): T = {
    import Preprocessor.tokenizeBySpace

    // each line is assumed to be a sentence containing tokens
    // separated by space
    val source = Source.fromFile(p.toFile)("UTF-8")
    try {
      logger.debug("Reading {}", p.toFile)
      f(source.getLines.toList.map(tokenizeBySpace))
    } catch {
      case e: Exception =>
        logger.error("Unable to read file: " + p.toFile, e)
        throw e
    } finally {
      source.close
    }
  }

  def readFiles(name: Option[String], source: Path): GenSeq[Document] = {
    logger.info("Finding files under {}", source)
    val paths = getFiles(source)
    if (paths.isEmpty) {
      logger.error("No text files found under {}", source)
      throw new IllegalArgumentException("No text files found files under " + source)
    }

    for (n <- name) saveFileList(n, paths)

    readFiles(paths)
  }

  def readFiles(paths: Vector[Path]) = {
    val cache = buildCache(paths)

    logger.info("Reading stopword file")
    implicit val stopwords = StopWords.implicits.default

    logger.info("Reading documents")
    paths.par.map(readFile { l =>
      new Document(l.map { ts =>
        val s = Sentence(ts.map(cache.apply))
        Sentence(Preprocessor.preprocess(3)(s).map(NGram.apply))
      })
    })
  }

  def getFiles(source: Path) =
    FileHelpers.findFiles(source, "txt").map(source.resolve)

  def saveFileList(name: String, paths: Seq[Path]) = {
    val writer = new PrintWriter(s"${name}.files.txt")
    paths.foreach(writer.println)
    writer.close
  }
}