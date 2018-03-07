package tm.corpus.nips

import java.io.InputStream
import java.text.SimpleDateFormat
import org.apache.commons.csv.CSVFormat
import java.io.InputStreamReader

import scala.collection.JavaConversions._

import tm.text.DataConverter
import java.io.FileInputStream
import java.io.PrintWriter
import java.nio.file.Paths
import tm.text.StopWords
import tm.text.DataConverter.Settings
import tm.text.StanfordLemmatizer
import tm.text.Preprocessor
import tm.util.Arguments
import org.slf4j.LoggerFactory

object Paper {
  val abstractMissingText = "Abstract Missing"

  def processAbstract(original: String) =
    if (original == abstractMissingText) None else Some(original)

}

case class Paper(id: Int, year: Int,
                 title: String, abs: Option[String], body: String)

object Parameters {
  object implicits {
    implicit val settings = DataConverter.Settings(
      concatenations = 2, minDf = (Int) => 6)
  }
}

object ExtractText {
  object Content extends Enumeration {
    type Content = Value
    val Abstract, Body, Both = Value
  }

  class Conf(args: Seq[String]) extends Arguments(args) {
    banner(s"Usage: ${ExtractText.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... input-file output-dir")
    val take = opt[Int](descr = "take the first n papers only")
    val content = opt[String](
      default = Some("Body"),
      descr = s"Content of the papers to be extracted.  It can be: ${Content.values.mkString(", ")}. Default: Body")
    val noLemma = opt[Boolean](descr = "No lemmatization. Default: false")
    val noSplit = opt[Boolean](descr = "No sentence splitting. Default: false")
    val inputFile = trailArg[String](descr = "Input file")
    val outputDir = trailArg[String](descr = "Output directory")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    //    implicit val settings =
    //      DataConverter.Settings(concatenations = conf.concatenations(), minCharacters = 3,
    //        wordSelector = WordSelector.byTfIdf(3, 0, .25, conf.maxWords()))
    //
    //    convert(conf.name(), Paths.get(conf.source()))

    run(conf.inputFile(), conf.outputDir(), conf.take.toOption,
      Content.withName(conf.content()), !conf.noLemma(), !conf.noSplit())

    //    val p = retrievePapers(conf.inputFile(), _.id == 5914)
    //    println(p.head.body)
  }

  val logger = LoggerFactory.getLogger(ExtractText.getClass)

  val minChars = 3

  def run(inputFile: String, outputDir: String,
          take: Option[Int], content: Content.Value,
          lemmatization:     Boolean,
          sentenceSplitting: Boolean) = {
    implicit val stopWords = StopWords.implicits.default
    implicit val settings = Parameters.implicits.settings

    val output = Paths.get(outputDir)
    if (!output.toFile.exists)
      output.toFile.mkdirs()

    val input = new FileInputStream(inputFile)
    logger.info(s"Extracting ${content.toString()} from ${inputFile}")

    val lines = take match {
      case Some(n) => read(input).take(n)
      case None    => read(input)
    }

    case class Result(id: Int, ex: Option[Exception])

    val results = lines.par.map { p =>
      logger.info(f"${p.id}%05d ${p.year} ${p.title}")

      try {
        val target = output.resolve(f"nips${p.id}%05d.txt")
        preprocess(p, content, lemmatization, sentenceSplitting).map(documentToString) match {
          case Some(text) =>
            val writer = new PrintWriter(target.toString)
            writer.println(text)
            writer.close
          case None =>
            logger.info(f"${p.id}%05d contains no content")
        }
        Result(p.id, None)
      } catch {
        case (ex: Exception) =>
          logger.error(s"Error reading paper: ${p.id}")
          if (content == Content.Abstract || content == Content.Both)
            logger.error(s"Abstract: ${p.abs}")
          if (content == Content.Body || content == Content.Both)
            logger.error(s"Body: ${p.body}")

          Result(p.id, Some(ex))
      }
    }

    input.close
    logger.info("Finished extracting papers")

    results.filter(_.ex.isDefined).foreach { p =>
      logger.error(s"Exception for ${p.id}: ${p.ex.get.getMessage}")
    }
  }

  def checkAbstractMissing(inputFile: String) = {
    val input = new FileInputStream(inputFile)
    read(input).filterNot(_.abs == "Abstract Missing").foreach { p =>
      println(s"${p.id} ${p.year} ${p.abs.getOrElse("").take(20).replaceAll("\n", " ")}")
    }
  }

  def retrievePapers(inputFile: String, filter: (Paper) => Boolean) = {
    val input = new FileInputStream(inputFile)
    val papers = read(input).filter(filter).toList
    input.close
    papers
  }

  def read(inputStream: InputStream): Iterable[Paper] = {
    // must be put here since it may not be thread-safe

    val records = CSVFormat.EXCEL.withHeader()
      .parse(new InputStreamReader(inputStream));
    records.view.map { r =>
      Paper(r.get("id").toInt, r.get("year").toInt, r.get("title"),
        Paper.processAbstract(r.get("abstract")), r.get("paper_text"))
    }
  }

  def documentToString(tokens: Seq[Seq[String]]) =
    tokens.map(_.mkString(" ")).mkString("\n")

  def preprocess(paper: Paper, content: Content.Value,
                 lemmatization: Boolean, sentenceSplitting: Boolean)(
    implicit stopwords: StopWords, settings: Settings): Option[Seq[Seq[String]]] = {
    val text = content match {
      case Content.Abstract => paper.abs
      case Content.Body     => Some(paper.body)
      case Content.Both     => paper.abs.map(_ + "\n" + paper.body)
    }

    text.map { t =>
      val sentences = StanfordLemmatizer
        .process(t, lemmatization, sentenceSplitting).sentences

      sentences.map(s => Preprocessor.EnglishPreprocessor(s.toString, minChars))
    }
  }

}