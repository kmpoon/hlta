package tm.pdf

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper
import tm.text.NGram
import tm.text.Preprocessor
import tm.text.Sentence
import tm.text.StanfordLemmatizer
import tm.text.StopWords
import tm.util.FileHelpers
import tm.text.DataConverter.Settings

object ExtractText extends App {
  val replaceNonAlnum = ("\\P{Alnum}".r, (m: Match) => "_")
  val replaceStartingDigit = ("^(\\p{Digit})".r, (m: Match) => s"_${m.group(1)}")

  def useRegexToReplace(pair: (Regex, (Match) => String)) = pair match {
    case (r, m) => (input: String) => r.replaceAllIn(input, m)
  }

  run

  def run() {
    if (args.length < 1) {
      printUsage
      return
    }

    import StopWords.implicits.default
    import Parameters.implicits.settings

    if (new File(args(0)).isDirectory()) {
      if (args.length < 2) {
        printUsage
        return
      }

      new File(args(1)).mkdirs()

      extractDirectory(args(0), args(1))
    } else {
      extractFile(args(0))
    }

  }

  def printUsage() = {
    println("ExtractText input_file")
    println("ExtractText input_dir output_dir")
  }

  def extractDirectory(inputDir: String, outputDir: String)(
    implicit stopwords: StopWords, settings: Settings) = {
    import FileHelpers.getPath

    val directory = new File(inputDir)
    val files = directory.list().filter(_.endsWith(".pdf"))
    files.par.foreach { f =>
      val inputFile = getPath(inputDir, f)
      val outputFile = getPath(outputDir, getOutputFile(f))
      extractFile(inputFile, outputFile)
    }
  }

  def getOutputFile(inputFile: String) = inputFile.replaceAll(".pdf$", ".txt")

  def extractFile(inputFile: String)(
    implicit stopwords: StopWords, settings: Settings): Unit =
    extractFile(inputFile, getOutputFile(inputFile))

  def extractText(inputFile: String)(implicit stopwords: StopWords): String = {
    val force = false;
    val sort = false;
    val separateBeads = true;
    val encoding = "UTF-8"
    val startPage = 1;
    val endPage = Integer.MAX_VALUE;

    val document = PDDocument.load(inputFile, false)
    val writer = new StringWriter()

    try {
      val stripper = new PDFTextStripper(encoding);
      stripper.setForceParsing(force);
      stripper.setSortByPosition(sort);
      stripper.setShouldSeparateByBeads(separateBeads);
      stripper.setStartPage(startPage);
      stripper.setEndPage(endPage);
      stripper.writeText(document, writer);

      undoHyphenation(writer.toString)
    } finally {
      document.close()
      writer.close()
    }
  }

  def preprocess(text: String)(
    implicit stopwords: StopWords, settings: Settings) = {
    val document = StanfordLemmatizer.process(text)

    def preprocess(s: Sentence): Seq[String] = s.tokens
      .map(_.toString.toLowerCase)
      .map(Preprocessor.normalize)
      .map(StanfordLemmatizer.bracketRegex.replaceAllIn(_, ""))
      .map(useRegexToReplace(replaceNonAlnum))
      .map(useRegexToReplace(replaceStartingDigit))
      .filter(withProperLength(settings.minCharacters))
      .filterNot(stopwords.contains)

    document.sentences.map(preprocess)
  }

  def withProperLength(minCharacters: Int)(word: String) = {
    word.replaceAll("[^\\p{Alpha}\\n]+", "").length >= minCharacters
  }

  def extractFile(inputFile: String, outputFile: String)(
    implicit stopwords: StopWords, settings: Settings): Unit = {
    try {
      val encoding = "UTF-8"
      val output = new PrintWriter(outputFile, encoding)

      val sentences = preprocess(extractText(inputFile)).filter(_.size > 0)
      output.write(sentences.map(_.mkString(" ")).mkString("\n"))

      output.close
    } catch {
      case e: Exception =>
        println(s"Error extracting file: ${inputFile}")
        println(e.getMessage)
    }

  }

  def undoHyphenation(text: String) =
    text.replaceAll("""-\n(\S+)(\s*)""", "$1\n")
}