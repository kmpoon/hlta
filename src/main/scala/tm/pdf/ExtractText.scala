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
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

object ExtractText {
  val replaceNonAlnum = ("\\P{Alnum}".r, (m: Match) => "_")
  val replaceStartingDigit = ("^(\\p{Digit})".r, (m: Match) => s"_${m.group(1)}")

  def useRegexToReplace(pair: (Regex, (Match) => String)) = pair match {
    case (r, m) => (input: String) => r.replaceAllIn(input, m)
  }

  def main(args: Array[String]) {
    if (args.length < 1) {
      printUsage
      return
    }

    import StopWords.implicits.default
    import Parameters.implicits.settings

    val source = Paths.get(args(0))
    if (Files.isDirectory(source)) {
      if (args.length < 2) {
        printUsage
        return
      }

      val dest = Paths.get(args(1))
      Files.createDirectories(dest)

      extractDirectory(source, dest)
    } else {
      extractFile(source)
    }
  }

  def printUsage() = {
    println("ExtractText input_file")
    println("ExtractText input_dir output_dir")
  }

  def extractDirectory(inputDir: Path, outputDir: Path)(
    implicit stopwords: StopWords, settings: Settings) = {
    import FileHelpers.getPath

    val files = FileHelpers.findFiles(inputDir, "pdf").par

    // create directories
    files.map(_.getParent).distinct.filterNot(_ == null).foreach { d =>
      Files.createDirectories(outputDir.resolve(d))
    }

    files.par.foreach { f =>
      val inputFile = inputDir.resolve(f)
      val outputFile = outputDir.resolve(getOutputFile(f))
      extractFile(inputFile, outputFile)
    }

    //    val directory = new File(inputDir)
    //    val files = directory.list().filter(_.endsWith(".pdf"))
    //    files.par.foreach { f =>
    //      val inputFile = getPath(inputDir, f)
    //      val outputFile = getPath(outputDir, getOutputFile(f))
    //      extractFile(inputFile, outputFile)
    //    }
  }

  def getOutputFile(inputFile: Path) =
    Paths.get(inputFile.toString.replaceAll(".pdf$", ".txt"))

  def extractFile(inputFile: Path)(
    implicit stopwords: StopWords, settings: Settings): Unit =
    extractFile(inputFile, getOutputFile(inputFile))

  def extractText(inputFile: Path)(implicit stopwords: StopWords): String = {
    val force = false;
    val sort = false;
    val separateBeads = true;
    //     val encoding = "UTF-8"
    val startPage = 1;
    val endPage = Integer.MAX_VALUE;

    val document = PDDocument.load(inputFile.toFile)
    val writer = new StringWriter()

    try {
      val stripper = new PDFTextStripper();
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

  def extractFile(inputFile: Path, outputFile: Path)(
    implicit stopwords: StopWords, settings: Settings): Unit = {
    try {
      val encoding = "UTF-8"
      val output = new PrintWriter(outputFile.toFile, encoding)

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