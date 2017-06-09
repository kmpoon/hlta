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

    implicit val settings =
      DataConverter.Settings(concatenations = conf.concatenations(), minCharacters = 3,
        wordSelector = WordSelector.byTfIdf(3, 0, .25, conf.maxWords()))

    convert(conf.name(), Paths.get(conf.source()))
  }

  val logger = LoggerFactory.getLogger(Convert.getClass)

  type Cache = Map[String, NGram]

  def convert(name: String, source: Path)(implicit settings: Settings) = {
    val documents = readFiles(name, source)
    DataConverter.convert(name, documents)
  }

  def buildCache(paths: Vector[Path]): Cache = {
    import Preprocessor.tokenizeBySpace

    logger.info("Building cache")
    mapReduce(paths.par)(
      readFile { _.flatten.toSet })(_ ++ _)
      .map(t => t -> NGram(t)).toMap
  }

  def readFile[T](f: (Seq[Seq[String]]) => T)(p: Path): T = {
    import Preprocessor.tokenizeBySpace

    // each line is assumed to be a sentence containing tokens
    // separated by space
    val source = Source.fromFile(p.toFile, "UTF8")
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

  def readFiles(name: String, source: Path): GenSeq[Document] = {
    logger.info("Finding files under {}", source)
    val paths = getFiles(source)
    if (paths.isEmpty) {
      logger.error("No text files found under {}", source)
      throw new IllegalArgumentException("No text files found files under " + source)
    }
    saveFileList(name, paths)
    readFiles(paths)
  }

  def readFiles(paths: Vector[Path]) = {
    val cache = buildCache(paths)

    logger.info("Reading documents")
    paths.par.map(readFile { l =>
      new Document(l.map(ts => Sentence(ts.map(cache.apply))))
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