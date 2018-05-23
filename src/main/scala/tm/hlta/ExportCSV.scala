package tm.hlta

import java.io.PrintWriter
import scala.io.Source
import tm.util.Reader

object ExportCSV {
  val delimiter = "\t"

  object SourceType extends Enumeration {
    val Titles, Topics, TopicTable = Value
  }

  def main(args: Array[String]) {
    if (args.length < 2)
      printUsage
    else {
      val outputFile = if (args.length > 2) Some(args(2)) else None
      SourceType.values.find(_.toString.equalsIgnoreCase(args(0))) match {
        case None => printUsage
        case Some(t) => run(t, args(1), outputFile)
      }
    }
  }

  def printUsage() = {
    println("ExportCSV data_type data_file [output_file]")
    println
    println("Data type can be Titles, Topics, or TopicTable")
    println("e.g. ExportCSV titles papers.titles.txt papers.titles.csv")
  }

  def run(t: SourceType.Value, inputFile: String, outputFile: Option[String]) = {
    val writer = new PrintWriter(
      outputFile.getOrElse(inputFile.replaceAll("""\.[^\.]*$""", ".csv")))
    t match {
      case SourceType.Titles => exportTitles(inputFile, writer)
      case SourceType.Topics => exportTopics(inputFile, writer)
      case SourceType.TopicTable => exportTopicTable(inputFile, writer)
    }

    writer.close
  }

  def exportTitles(inputFile: String, writer: PrintWriter) = {
    writer.println(Seq("title", "conference", "year").mkString(delimiter))
    TitleFile.readDocuments(Source.fromFile(inputFile).getLines).foreach(
      d => writer.println(
        Seq(d.title, d.conference, d.year.toString).mkString(delimiter)))
  }

  def exportTopics(inputFile: String, writer: PrintWriter) = {
    val data = Reader.readData(inputFile)

    writer.println(data.variables.map(_.getName).mkString(delimiter))
    data.instances.foreach(i => writer.println(i.denseValues(data.variables.size).mkString(delimiter)))
  }

  def exportTopicTable(inputFile: String, writer: PrintWriter) = {
    writer.println(Seq("name", "level", "words").mkString(delimiter))

    val topics = HTMLTopicTable.readTopics(inputFile)
    topics.foreach(_ match {
      case (topic, parent) => writer.println(
        Seq(topic.name, topic.level.get, topic.words.mkString(","))
          .mkString(delimiter))
    })
  }
}