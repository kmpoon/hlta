package tm.text

import java.io.File
import scala.io.Source
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
    
    val language = opt[String](default = Some("en"), descr = "Language, default as English, can be {english, chinese, nonascii}")//ISO 639-1 language code or general name both accepted    
    val minChar = opt[Int](
      default = None,
      descr = "Minimum number of characters of a word to be selected. English default as 3, Chinese/Nonascii default as 1")
      
    val minDocFraction = opt[Double](
      default = Some(0.0),
      descr = "Minimum fraction of documents that a token can appear to be selected. Default: 0.0")
    val maxDocFraction = opt[Double](
      default = Some(0.25),
      descr = "Maximum fraction of documents that a token can appear to be selected. Default: 0.25")
    val seedWords = opt[String](descr = "File containing tokens to be included, regardless of other selection criteria.")
//    val seedNumber = opt[Int](descr = "Number of seed tokens to be included. Need to be specified when seed file is given.")
    val stopWords = opt[String](default = None, descr = "File of stop words, default using built-in stopwords list")
    
    val inputExt = opt[List[String]](default = Some(List("txt", "pdf")), descr = "Look for these extensions if a directory is given, default \"txt pdf\"")
    val inputEncoding = opt[String](default = Some("UTF-8"), descr = "Input .txt encoding, default UTF-8, see java.nio.charset.Charset for available encodings")
    
    val outputHlcm = opt[Boolean](default = Some(false), descr = "Additionally output hlcm format")
    val outputArff = opt[Boolean](default = Some(false), descr = "Additionally output arff format")
    val outputLda = opt[Boolean](default = Some(false), descr = "Additionally output lda format")
    
    val testsetRatio = opt[Double](default = Some(0.0), descr = "Split into training and testing set by a user given ratio. Default is 0.0")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
      
    val stopWords = if(conf.stopWords.isDefined){
                      logger.info("Reading stopword file")
                      StopWords.read(conf.stopWords())(conf.inputEncoding()) 
                    }
                    else conf.language().toLowerCase() match{
                      case "en" | "english" => StopWords.EnglishStopwords()
                      case "zh" | "chinese" => StopWords.ChineseStopwords()
                      case "nonascii" | _ => StopWords.Empty()
                    }
    val seedWords = if(conf.seedWords.isDefined){
                      logger.info("Using seed tokens from file: {}", conf.seedWords())
                      SeedTokens.read(conf.seedWords())(conf.inputEncoding())
                    }
                    else SeedTokens.Empty()
    val minChar = if(conf.minChar.isDefined) conf.minChar()
                  else conf.language().toLowerCase() match{
                    case "en" | "english" => 3
                    case "zh" | "chinese" => 1
                    case "nonascii" | _ => 1
                  }
    val engLemma = Lemmatization.EnglishLemma()
    def preprocessor(text: String) = {
      val tokens = conf.language().toLowerCase() match{
        case "en" | "english" => {
          val preprcoessed = Preprocessor.EnglishPreprocessor(text, minChars = minChar, stopwords = stopWords)
          preprcoessed.map(engLemma.lemma)
        }
        case "zh" | "chinese" => Preprocessor.ChinesePreprocessor(text, minChars = minChar, stopwords = stopWords)
        case "nonascii" | _ => Preprocessor.NonAsciiPreprocessor(text, minChars = minChar, stopwords = stopWords)
      }
      Document(Sentence(tokens))
    }
    val wordSelector = WordSelector.byTfIdf(minChar, conf.minDocFraction(), conf.maxDocFraction())
        
    val path = Paths.get(conf.source())
    val data = {
      if(java.nio.file.Files.isDirectory(path)){
        
        val dir = path
        logger.info("Finding files under {}", dir.toString())
        val paths = FileHelpers.findFiles(dir, conf.inputExt())
        if (paths.isEmpty) {
          //logger.error("No suitable file found under {}", dir)
          throw new IllegalArgumentException("No files found under " + dir + " with extension {" +conf.inputExt().mkString(",")+ "}")
        }else{
          logger.info("Found "+paths.size+" files")
        }
        
        //Write file order
        val writer = new PrintWriter(s"${conf.name()}.files.txt")
        paths.foreach(writer.println)
        writer.close
        
        apply(conf.name(), conf.maxWords(), paths = paths, encoding = conf.inputEncoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat(), seedWords = seedWords)
        
      }else{
        
        apply(conf.name(), conf.maxWords(), path = path, encoding = conf.inputEncoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat(), seedWords = seedWords)
        
      }
    }
    logger.info("done")
    
    if(conf.testsetRatio() > 0.0 && conf.testsetRatio() < 1.0){
      val (testingData, trainingData) = data.randomSplit(conf.testsetRatio())
      if(testingData.size()==0)
        logger.error("Testing data of size 0. Check your testset ratio.")
      if(trainingData.size()==0)
        logger.error("Training data of size 0. Check your testset ratio.")
      if(conf.outputArff()){
        logger.info("Saving in ARFF format (count data)")
        trainingData.saveAsArff(s"${conf.name()}.train.arff")
        testingData.saveAsArff(s"${conf.name()}.test.arff")
      }
      if(conf.outputHlcm()){
        logger.info("Saving in HLCM format (binary data)")
        trainingData.binary().saveAsHlcm(s"${conf.name()}.train.hlcm")
        testingData.binary().saveAsHlcm(s"${conf.name()}.test.hlcm")
      }
      if(conf.outputLda()){
        logger.info("Saving in LDA format (count data)")
        trainingData.saveAsLda(s"${conf.name()}.train.lda.txt", s"${conf.name()}.train.vocab.txt")
        testingData.saveAsLda(s"${conf.name()}.test.lda.txt", s"${conf.name()}.test.vocab.txt")
      }
      logger.info("Saving in sparse data format (binary data)")
      trainingData.saveAsTuple(s"${conf.name()}.train.sparse.txt")
      testingData.saveAsTuple(s"${conf.name()}.test.sparse.txt")
    }else{
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
        data.saveAsLda(s"${conf.name()}.lda.txt", s"${conf.name()}.vocab.txt")
      }
      logger.info("Saving in sparse data format (binary data)")
      data.saveAsTuple(s"${conf.name()}.sparse.txt")
    }
    
  }
  
  def defaultPreprocessor(text: String) = {
    val tokens = Preprocessor.EnglishPreprocessor(text, minChars = 3, stopwords = StopWords.EnglishStopwords())
    Document(Sentence(tokens))
  }

  /**
   * For external call
   */
  def apply(name: String, maxWords: Int, path: Path = null, paths: Vector[Path] = null,
      encoding: String = "UTF-8", preprocessor: (String) => Document = defaultPreprocessor, 
      wordSelector: WordSelector = WordSelector.basic(), concat: Int = 2,
      seedWords: SeedTokens = SeedTokens.Empty()) = {
    val documents = {
      if(paths != null)
        readFiles(paths, preprocessor(_), encoding = encoding)
      else if(path != null)
        readLines(path, preprocessor(_), encoding = encoding)
      else
        throw new Exception("Either path or paths must be given")
    }
    DataConverter(name, documents, maxWords = maxWords, concat = concat, seedWords = seedWords, wordSelector = wordSelector)
  }

  val logger = LoggerFactory.getLogger(Convert.getClass)

//  @Deprecated
//  type Cache = Map[String, NGram]
//
//  @Deprecated
//  def convert(name: String, source: Path, maxWords: Int, seeds: Option[SeedTokens])(
//    implicit settings: DataConverter.Settings) = {
//    val documents = readFiles(Some(name), source)
//    DataConverter.convert(name, documents, maxWords, seeds)
//  }
//  
//  @Deprecated
//  def buildCache(paths: Vector[Path]): Cache = {
//    logger.info("Building cache")
//    mapReduce(paths.par)(readFile { _.map(s => Preprocessor.tokenizeBySpace(s)).flatten.toSet })(_ ++ _)
//      .map(t => t -> NGram(t)).toMap
//  }
  
  def readFiles[T](paths: Vector[Path], f: String => T, encoding: String): GenSeq[T] = {      
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
      source.getLines.map{ line =>
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
  
  def readCsv[T](path: Path, field: String, f: String => T, encoding: String): GenSeq[T] = {      
    //val cache = buildCache(paths)  
    logger.info("Reading documents")
    import com.github.tototoshi.csv._
    val reader = CSVReader.open(new File(path.toString()), encoding)
    //val source = Source.fromFile(path.toFile())(encoding)
    try {
      logger.debug("Reading {}", path.toFile())
      reader.iteratorWithHeaders.map{ line =>
        f(line(field))
      }.toVector
    } catch {
      case e: Exception =>
        logger.error("Unable to read file: " + path.toFile, e)
        throw e
    } finally {
      reader.close()
    }
  }

//  @Deprecated
//  def readFiles(name: Option[String], source: Path): GenSeq[Document] = {
//    logger.info("Finding files under {}", source)
//    val paths = getFiles(source)
//    if (paths.isEmpty) {
//      logger.error("No text files found under {}", source)
//      throw new IllegalArgumentException("No text files found files under " + source)
//    }
//
//    for (n <- name) saveFileList(n, paths)
//
//    readFiles(paths)
//  }
//  
//  @Deprecated
//  def readFiles(paths: Vector[Path]) = {
//    val cache = buildCache(paths)
//
//    logger.info("Reading stopword file")
//    implicit val stopwords = StopWords.implicits.default
//
//    logger.info("Reading documents")
//    paths.par.map(readFile { l =>
//      new Document(l.map { ts =>
//        val tokens = Preprocessor.EnglishPreprocessor(ts, minChars = 3)
//        Sentence(tokens)
//      })
//    })
//  }
//  
  @Deprecated
  def readFile[T](f: Seq[String] => T)(p: Path): T = {

    // each line is assumed to be a sentence containing tokens
    // separated by space
    val source = Source.fromFile(p.toFile)("UTF-8")
    try {
      logger.debug("Reading {}", p.toFile)
      f(source.getLines.toList)
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

//  @Deprecated
//  def saveFileList(name: String, paths: Seq[Path]) = {
//    val writer = new PrintWriter(s"${name}.files.txt")
//    paths.foreach(writer.println)
//    writer.close
//  }  
  
}