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
    banner("Usage: tm.text.Convert [OPTION]... source name max-words")
    val source = trailArg[String](descr = "Source directory or source file, if dir, 1 file = 1 doc; if file, 1 line = 1 doc")
    val name = trailArg[String](descr = "Name of data, default as \"data\"")
    val maxWords = trailArg[Int](descr = "Dictionary size, maximum number of words (n-gram)")
    
    val nonAscii = opt[Boolean](default = Some(false), descr = "Accept non ascii as well")
    val minChar = opt[Int](default = Some(3), descr = "Minimum (1-gram) word length, default as 3")
    val concat = opt[Int](default = Some(1), descr = "Concatenate word to procude n-gram, where n=2^c, default as 1")
    val stopWords = opt[String](default = None, descr = "stopword file, default \"stopwords-lewis.csv\" inside the package")
    
    val input = new Subcommand("input") {
      val ext = opt[List[String]](default = Some(List("txt", "pdf")), descr = "Look for these extensions if a directory is given, default \"txt pdf\"")
      val encoding = opt[String](default = Some("UTF-8"), descr = "Input .txt encoding, default UTF-8, see java.nio.charset.Charset for available encodings")
    }
    addSubcommand(input)
    
    val output = new Subcommand("output") {
      val hlcm = opt[Boolean](default = Some(false), descr = "Additionally output hlcm format")
      val arff = opt[Boolean](default = Some(false), descr = "Additionally output arff format")
    }
    addSubcommand(output)

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    implicit val settings =
      Settings(concatenations = conf.concat(), minCharacters = conf.minChar(),
        wordSelector = WordSelector.ByTfIdf(conf.minChar(), 0, .25, conf.maxWords()), asciiOnly = !conf.nonAscii(), 
        stopWords = if(conf.stopWords.isDefined) conf.stopWords() else null)

    val (data, paths) = apply(conf.name(), conf.source(), extensions = conf.input.ext(), encoding = conf.input.encoding())
    logger.info("done")
    
    //Write file order
    val writer = new PrintWriter(s"${conf.name()}.files.txt")
    paths.foreach(writer.println)
    writer.close
    
    if(conf.output.arff()){
      logger.info("Saving in ARFF format (count data)")
      data.saveAsArff(s"${conf.name()}.arff")
    }
    if(conf.output.hlcm()){
      logger.info("Saving in HLCM format (binary data)")
      data.binary().saveAsHlcm(s"${conf.name()}.hlcm")
    }
    logger.info("Saving in sparse data format (binary data)")
    data.saveAsTuple(s"${conf.name()}.sparse.txt")
  }

  /**
   * For external call
   * 
   * Returns (data: Data, paths: Seq[String])
   */
  def apply(name: String, path: String, extensions: List[String] = List("txt", "pdf"), encoding: String = "UTF-8")(implicit settings: Settings) = {
    def preprocessor(text: String) = {
      val cleanedText = Preprocessor.preprocess(text, minChars = settings.minCharacters, asciiOnly = settings.asciiOnly)
      val tokens = Preprocessor.tokenizeBySpace(cleanedText)
      Document(Sentence(tokens))
  //      new Document(l.map{ts => 
  //        Sentence(ts.mkString(" "))
  //        val s = Sentence(ts.map(cache.apply))
  //        Sentence(Preprocessor.preprocess(3)(s).map(NGram.apply))
  //      })
      
      // each line is assumed to be a sentence containing tokens
      // separated by space
      // TODO: continue keeping this unimplemented
    }
    
    val _path = Paths.get(path)
    
    val (documents, paths) = {
      if(java.nio.file.Files.isDirectory(_path))
        readDirectory(_path, preprocessor(_), extensions = extensions, encoding = encoding)
      else
        readLines(_path, preprocessor(_), encoding = encoding)
    }
    val data = DataConverter(name, documents)
    
    (data, paths)
  }
  
  /**
   * minDf is computed when the number of documents is given.
   */
  class Settings(val concatenations: Int, val minCharacters: Int,
    val wordSelector: WordSelector, val asciiOnly: Boolean, val stopWords : StopWords)

  object Settings{
    def apply(concatenations: Int = 0, minCharacters: Int = 3, minTf: Int = 6,
      minDf: (Int) => Int = (Int) => 6, wordSelector: WordSelector = null, asciiOnly: Boolean = true, stopWords: String = null): Settings = {
      val _wordSelector = wordSelector match{
        case null => WordSelector.Basic(minCharacters, minTf, minDf)
        case _ => wordSelector
      }
      
      val _stopWords = stopWords match{
        case null => StopWords.implicits.default
        case _ => logger.info("Reading stopword file"); StopWords.read(stopWords)
      }
        
      new Settings(concatenations, minCharacters, _wordSelector, asciiOnly, _stopWords)
    }
  }

  val logger = LoggerFactory.getLogger(Convert.getClass)

  type Cache = Map[String, NGram]

//  def buildCache(paths: Vector[Path]): Cache = {
//    import Preprocessor.tokenizeBySpace
//
//    logger.info("Building cache")
//    mapReduce(paths.par)(
//      readFile { _.flatten.toSet })(_ ++ _)
//      .map(t => t -> NGram(t)).toMap
//  }
  
  def readDirectory[T](source: Path, f: String => T, extensions: List[String], encoding: String): (GenSeq[T], GenSeq[String]) = {       
    //Scan directory
    logger.info("Finding files under {}", source)
    val paths = FileHelpers.findFiles(source, extensions).map(source.resolve)
    if (paths.isEmpty) {
      logger.error("No text files found under {}", source)
      throw new IllegalArgumentException("No text files found files under " + source)
    }
        
    readFiles(paths, f, encoding)
  }
  
  def readFiles[T](paths: Vector[Path], f: String => T, encoding: String): (GenSeq[T], GenSeq[String]) = {      
    //val cache = buildCache(paths)
    logger.info("Reading documents")
    paths.par.map{ path => // each path(file) is one document
      val extension = path.toString().split('.').last
      extension match {
        case "pdf" =>      
          val document = PDDocument.load(path.toFile)      
          try {
            val stripper = new PDFTextStripper();
            stripper.setForceParsing(false);
            stripper.setSortByPosition(false);
            stripper.setShouldSeparateByBeads(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Integer.MAX_VALUE);
            val text = stripper.getText(document).replaceAll("""-\n(\S+)(\s*)""", "$1\n") //undo hyphenation
            (f(text), path.toString)
          } catch {
            case e: Exception =>
              logger.error("Unable to read file: " + path.toFile, e)
              throw e
          } finally {
            document.close()
          }
        case _ =>  //including txt
          val source = Source.fromFile(path.toFile)(encoding)
          try {
            logger.debug("Reading {}", path.toFile)
            (f(source.getLines.mkString(" ")), path.toString)
          } catch {
            case e: Exception =>
              logger.error("Unable to read file: " + path.toFile, e)
              throw e
          } finally {
            source.close
          }
      } 
    }.unzip
  }
  
  def readLines[T](path: Path, f: String => T, encoding: String): (GenSeq[T], GenSeq[String]) = {      
    //val cache = buildCache(paths)  
    logger.info("Reading documents")
    val source = Source.fromFile(path.toFile())(encoding)
    try {
      logger.debug("Reading {}", path.toFile())
      source.getLines.zipWithIndex.map{ case (line, lineNumber) =>
        (f(line), "Line"+lineNumber)
      }.toVector.unzip
    } catch {
      case e: Exception =>
        logger.error("Unable to read file: " + path.toFile, e)
        throw e
    } finally {
      source.close
    }
  }
  
}