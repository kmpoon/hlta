package tm.corpus

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import tm.text.Preprocessor
import tm.text.Sentence
import tm.text.StanfordLemmatizer
import tm.text.StopWords
import tm.util.Arguments
import tm.util.FileHelpers

trait Extractor {
  def extension: String

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  case class Parameters(stopwords: StopWords, minCharacters: Int = 3,
                        lemmatization: Boolean = true, sentenceSplitting: Boolean = true,
                        take: Option[Int] = None)

  class Conf(args: Seq[String], c: Class[_]) extends Arguments(args) {
    banner(s"Usage: ${c.getName.replaceAll("\\$$", "")} [Options] [input-file | input-dir output-dir]")
    val take = opt[Int](descr = "take the first n papers only")
    val minCharacters = opt[Int](
      descr = "Minimum number of characters for a word to be included. Default: 3.",
      default = Some(3))
    val noLemma = opt[Boolean](descr = "No lemmatization. Default: false")
    val noSplit = opt[Boolean](descr = "No sentence splitting. Default: false")
    val noPreprocess = opt[Boolean](descr = "Do not preprocess the text before saving them. Default: false")
    val input = trailArg[String](descr = "Input file or input directory")
    val output = trailArg[String](
      descr = "Output directory, if the first argument is an input directory.",
      required = false)

    verify
    checkDefaultOpts()
  }

  def run(args: Array[String]) = {
    val conf = new Conf(args, this.getClass)

    implicit val parameters = Parameters(
      stopwords = StopWords.implicits.default,
      minCharacters = conf.minCharacters(),
      !conf.noLemma(), !conf.noSplit(), conf.take.toOption)

    val source = Paths.get(conf.input())
    if (Files.isDirectory(source)) {
      if (conf.output.isDefined) {
        val dest = Paths.get(conf.output())
        // create output directory
        Files.createDirectories(dest)
        extractDirectory(source, dest, conf.noPreprocess())
      } else {
        conf.printHelp()
      }
    } else {
      val current = Paths.get(".")
      extractFile(source, current, current, conf.noPreprocess())
    }
  }

  def extractDirectory(inputDir: Path, outputDir: Path, noPreprocess: Boolean)(
    implicit parameters: Parameters) = {

    def takeOrAll(files: Seq[Path]) = {
      parameters.take match {
        case Some(n) => files.take(n)
        case None => files
      }
    }

    val files = takeOrAll(findFiles(inputDir)).par

    // create directories inside output directory
    files.map(_.getParent).distinct.filterNot(_ == null).foreach { d =>
      Files.createDirectories(outputDir.resolve(d))
    }

    files.par.foreach { f =>
      try {
        extractFile(f, inputDir, outputDir, noPreprocess)
      } catch {
        case ex: Exception =>
          logger.error("Error occurred when reading file: {}", f)
      }
    }
  }

  def findFiles(inputDir: Path) =
    FileHelpers.findFiles(inputDir, List(extension), addBase = false)

  def getOutputFile(inputFile: Path) =
    inputFile.toString.replaceAll(s".${extension}$$", ".txt")

  /**
   * {@code inputFile} holds the path relative to the inputDir.  It uses
   * a relative form so that the output file can be made relative to the
   * output directory.
   */
  def extractFile(inputFile: Path, inputDir: Path, outputDir: Path, noPreprocess: Boolean)(
    implicit parameters: Parameters): Unit = {

    for ((filename, content) <- extractText(inputDir, inputFile).par) {
      var output: Option[PrintWriter] = None
      try {
        val text = if (noPreprocess) {
          content
        } else {
          val sentences = preprocess(
            content, parameters.lemmatization, parameters.sentenceSplitting)
            .filter(_.size > 0)
          sentences.map(_.mkString(" ")).mkString("\n")
        }
        val outputFile = outputDir.resolve(filename)

        if (text.size > 0) {
          val encoding = "UTF-8"
          output = Some(new PrintWriter(outputFile.toFile, encoding))
          output.foreach(_.write(text))
        } else {
          logger.info("The content becomes empty after preprocessing for the output file: {}", outputFile)
        }
      } catch {
        case e: Exception =>
          logger.error("Error extracting file: {}", inputFile)
          logger.error(e.getMessage)
      } finally {
        output.foreach(_.close)
      }

    }

  }

  def extractText(inputDir: Path, inputFile: Path): Seq[(String, String)] =
    (getOutputFile(inputFile), extractSingleText(inputDir.resolve(inputFile))) +: Nil

  /**
   * To be overridden for those cases where a single file corresponds
   * to one document.
   */
  def extractSingleText(inputFile: Path): String = ???

  def preprocess(text: String, lemmatization: Boolean = true,
                 sentenceSplitting: Boolean = true)(
                  implicit parameters: Parameters) = {
    val document =
      StanfordLemmatizer.process(text, lemmatization, sentenceSplitting)

    def preprocess(s: Sentence): Seq[String] = s.tokens
      .map(_.toString.toLowerCase)
      .map(Preprocessor.normalize)
      .map(StanfordLemmatizer.bracketRegex.replaceAllIn(_, ""))
      .map(useRegexToReplace(replaceNonAlnum))
      .map(useRegexToReplace(replaceStartingDigit))
      .filter(withProperLength(parameters.minCharacters))
      .filterNot(parameters.stopwords.contains)

    document.sentences.map(preprocess)
  }

  val replaceNonAlnum = ("\\P{Alnum}".r, (m: Match) => "_")
  val replaceStartingDigit = ("^(\\p{Digit})".r, (m: Match) => s"_${m.group(1)}")

  def useRegexToReplace(pair: (Regex, (Match) => String)) = pair match {
    case (r, m) => (input: String) => r.replaceAllIn(input, m)
  }

  def withProperLength(minCharacters: Int)(word: String) = {
    word.replaceAll("[^\\p{Alpha}\\n]+", "").length >= minCharacters
  }

  def undoHyphenation(text: String) =
    text.replaceAll("""-(\r\n|\n|\r)(\S+)(\s*)""", "$2\n")
}