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

object Convert {
  val logger = LoggerFactory.getLogger(Convert.getClass)

  type Cache = Map[String, NGram]

  def convert(name: String, source: Path)(implicit settings: Settings) = {
    val documents = readFiles(name, source)
    DataConverter.convert(name, documents)
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
    val source = Source.fromFile(p.toFile)
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

//  def readFiles(name: String, source: Path): Seq[Document] = {
//    trait O { def toD(): D }
//    class D(val ds: Vector[Document], val c: Map[NGram, NGram]) extends O {
//      def toD() = this
//      def +(d: D): D = ???
//    }
//    class P(p: Path) extends O {
//      def toD(): D = {
//        readFile { ls =>
//          val cache = ls.flatten.map { t => t -> NGram(t) }.toMap
//          val lines = ls.map(_.map(cache.apply))
//          (p, lines, cache)
//        }(p)
//      }
//    }
//
//    getFiles(source).par.map(new P(_): O).reduce(_.toD + _.toD).toD.ds
//
//  }

  def readFiles(name: String, source: Path): GenSeq[Document] = {
    logger.info("Finding files under {}", source)
    val paths = getFiles(source)
    
    val cache = buildCache(paths)
    
    logger.info("Reading documents")
    paths.par.map(readFile { l =>
      new Document(l.map(ts => Sentence(ts.map(cache.apply))))
    })
  }

  //  def readFile(p: Path): (Path, Seq[Seq[NGram]], Cache) = {
  //    import Preprocessor.tokenizeBySpace
  //
  //    // each line is assumed to be a sentence containing tokens
  //    // separated by space
  //    val source = Source.fromFile(p.toFile)
  //    try {
  //      logger.debug("Reading {}", p.toFile)
  //      val tokenLines = source.getLines.toList.map(tokenizeBySpace)
  //
  //      // use a cache to reduce the number of objects (c.f. flyweight pattern)
  //      val cache = tokenLines.flatten.map { t =>
  //        val n = NGram(t)
  //        n -> n
  //      }.toMap
  //      val lines = tokenLines.map(_.map(NGram.apply).map(cache.apply))
  //      (p, lines, cache)
  //    } catch {
  //      case e: Exception =>
  //        logger.error("Unable to read file: " + p.toFile, e)
  //        throw e
  //    } finally {
  //      source.close
  //    }
  //  }

  //  def readFile(p: Path): (Path, Seq[Seq[NGram]], Cache) = {
  //    import Preprocessor.tokenizeBySpace
  //
  //    // each line is assumed to be a sentence containing tokens
  //    // separated by space
  //    val source = Source.fromFile(p.toFile)
  //    try {
  //      logger.debug("Reading {}", p.toFile)
  //      val tokenLines = source.getLines.toList.map(tokenizeBySpace)
  //
  //      // use a cache to reduce the number of objects (c.f. flyweight pattern)
  //      val cache = tokenLines.flatten.map(t => (t, NGram(t))).toMap
  //      val lines = tokenLines.map(_.map(cache.apply))
  //      (p, lines, cache)
  //    } catch {
  //      case e: Exception =>
  //        logger.error("Unable to read file: " + p.toFile, e)
  //        throw e
  //    } finally {
  //      source.close
  //    }
  //  }

  def getFiles(source: Path) =
    FileHelpers.findFiles(source, "txt").map(source.resolve)
}