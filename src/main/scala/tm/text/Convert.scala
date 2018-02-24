package tm.text

import java.io.File
import scala.io.Source
import tm.text.StopWords.implicits
import tm.util.FileHelpers
import java.io.PrintWriter
import java.nio.file.Paths
import java.nio.file.Path
import org.slf4j.LoggerFactory
import scala.collection.GenMap
import scala.collection.GenSeq
import tm.util.ParMapReduce._

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper

import org.rogach.scallop._
import tm.util.Arguments

object Convert {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("Usage: tm.text.Convert [OPTION]... name source max-words concat")
    val name = trailArg[String](descr = "Name of data, default as \"data\"")
    val source = trailArg[String](descr = "Source directory or source file, if dir, 1 file = 1 doc; if file, 1 line = 1 doc")
    val maxWords = trailArg[Int](descr = "Dictionary size, maximum number of words (n-gram)")
    val concat = trailArg[Int](descr = "Concatenate words/tokens to produce n-grams with the given number of repetitions, where n can be 2^c.  Default is 0")
    
    val nonAscii = opt[Boolean](default = Some(false), descr = "Accept non ascii as well")
    val minChar = opt[Int](
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
    val stopWords = opt[String](default = None, descr = "stopword file, default \"stopwords-lewis.csv\" inside the package")
    
    val inputExt = opt[List[String]](default = Some(List("txt", "pdf")), descr = "Look for these extensions if a directory is given, default \"txt pdf\"")
    val inputEncoding = opt[String](default = Some("UTF-8"), descr = "Input .txt encoding, default UTF-8, see java.nio.charset.Charset for available encodings")
    
    val outputHlcm = opt[Boolean](default = Some(false), descr = "Additionally output hlcm format")
    val outputArff = opt[Boolean](default = Some(false), descr = "Additionally output arff format")
    val outputLda = opt[Boolean](default = Some(false), descr = "Additionally output lda format")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
    
    val seed = conf.seedFile.toOption.map(s => SeedTokens.read(s))
    for (s <- seed) {
      if (s.max > 2) throw new IllegalArgumentException(
        s"The file ${conf.seedFile} contains n-grams with n = ${s.max}. Currently only n = 2 is supported.")

      logger.info("Using seed tokens from file: {}", conf.seedFile)
    }

    implicit val settings =
      DataConverter.Settings(concatenations = conf.concat(), minCharacters = conf.minChar(), maxWords = conf.maxWords(),
        wordSelector = WordSelector.ByTfIdf(
          conf.minChar(), conf.minDocFraction(),
          conf.maxDocFraction()), asciiOnly = !conf.nonAscii(), 
        stopWords = if(conf.stopWords.isDefined) conf.stopWords() else null,
        seedWords = if(conf.seedFile.isDefined) seed else None)

    val path = Paths.get(conf.source())
    val data = if(java.nio.file.Files.isDirectory(path)){
      val dir = path
      
      logger.info("Finding files under {}", dir.toString())
      val paths = FileHelpers.findFiles(dir, conf.inputExt()).map(dir.resolve)
      if (paths.isEmpty) {
        logger.error("No suitable file found under {}", dir)
        throw new IllegalArgumentException("No text files found files under " + dir)
      }
      
      //Write file order
      val writer = new PrintWriter(s"${conf.name()}.files.txt")
      paths.foreach(writer.println)
      writer.close
      
      apply(conf.name(), paths = paths, encoding = conf.inputEncoding())
    }else{
      apply(conf.name(), path = path, encoding = conf.inputEncoding())
    }
    logger.info("done")
    
    if(conf.outputArff()){
      logger.info("Saving in ARFF format (count data)")
      data.saveAsArff(s"${conf.name()}.arff")
    }
    if(conf.outputHlcm()){
      logger.info("Saving in HLCM format (binary data)")
      data.binary().saveAsHlcm(s"${conf.name()}.hlcm")
    }
    if(conf.outputLda()){
      logger.info("Saving in LDA format (count data)")
      data.binary().saveAsLda(s"${conf.name()}.lda.txt", s"${conf.name()}.vocab.txt")
    }
    logger.info("Saving in sparse data format (binary data)")
    data.saveAsTuple(s"${conf.name()}.sparse.txt")
  }

  /**
   * For external call
   * 
   * Returns (data: Data, paths: Seq[String])
   */
  def apply(name: String, path: Path = null, paths: Vector[Path] = null, encoding: String = "UTF-8")
  (implicit settings: DataConverter.Settings) = {
    def preprocessor(text: String) = {
      val cleanedText = Preprocessor.preprocess(text, minChars = settings.minCharacters, asciiOnly = settings.asciiOnly)
      val tokens = Preprocessor.tokenizeBySpace(cleanedText)
      Document(Sentence(tokens))
    }

    val documents = {
      if(paths != null)
        readFiles(paths, preprocessor(_), encoding = encoding)
      else if(path != null)
        readLines(path, preprocessor(_), encoding = encoding)
      else
        throw new Exception("Either path or paths must be given")
    }
    DataConverter(name, documents)
  }

  val logger = LoggerFactory.getLogger(Convert.getClass)

  @Deprecated
  type Cache = Map[String, NGram]

  @Deprecated
  def convert(name: String, source: Path, maxWords: Int, seeds: Option[SeedTokens])(
    implicit settings: DataConverter.Settings) = {
    val documents = readFiles(Some(name), source)
    DataConverter.convert(name, documents, maxWords, seeds)
  }
  
  @Deprecated
  def buildCache(paths: Vector[Path]): Cache = {
    import Preprocessor.tokenizeBySpace

    logger.info("Building cache")
    mapReduce(paths.par)(readFile { _.flatten.toSet })(_ ++ _)
      .map(t => t -> NGram(t)).toMap
  }
  
  def readFiles[T](paths: Vector[Path], f: String => T, encoding: String): GenSeq[T] = {      
    //val cache = buildCache(paths)
    logger.info("Reading documents")
    paths.par.map{ path => // each path(file) is one document
      val extension = path.toString().split('.').last
      extension match {
        case "pdf" =>      
          val text = tm.corpus.pdf.ExtractText.extractSingleText(path)
          f(text)
        case _ =>  //including txt
          val source = Source.fromFile(path.toFile)(encoding)
          try {
            logger.debug("Reading {}", path.toFile)
            f(source.getLines.mkString(" "))
          } catch {
            case e: Exception =>
              logger.error("Unable to read file: " + path.toFile, e)
              throw e
          } finally {
            source.close
          }
      } 
    }
  }
  
  def readLines[T](path: Path, f: String => T, encoding: String): GenSeq[T] = {      
    //val cache = buildCache(paths)  
    logger.info("Reading documents")
    val source = Source.fromFile(path.toFile())(encoding)
    try {
      logger.debug("Reading {}", path.toFile())
      source.getLines.zipWithIndex.map{ case (line, lineNumber) =>
        f(line)
      }.toVector
    } catch {
      case e: Exception =>
        logger.error("Unable to read file: " + path.toFile, e)
        throw e
    } finally {
      source.close
    }
  }

  @Deprecated
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
  
  @Deprecated
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
  
  @Deprecated
  def readFile[T](f: Seq[Seq[String]] => T)(p: Path): T = {
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
  
  def getFiles(source: Path) =
    FileHelpers.findFiles(source, "txt").map(source.resolve)

  @Deprecated
  def saveFileList(name: String, paths: Seq[Path]) = {
    val writer = new PrintWriter(s"${name}.files.txt")
    paths.foreach(writer.println)
    writer.close
  }  
  
}