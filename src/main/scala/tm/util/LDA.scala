package tm.util

import org.latlab.util.DataSet
import org.latlab.util.DataSetLoader
import java.io.PrintWriter
import collection.JavaConversions._
import org.slf4j.LoggerFactory
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.FileInputStream
import java.io.BufferedInputStream
import scala.io.Source
import scala.annotation.tailrec

object ConvertDataToLDAFormat {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner(s"Usage: tm.util.ConvertDataToLDAFormat [OPTION]... input_data")
    val binaryValue = opt[Boolean](descr = "Use binary value (i.e. 0 or 1 only) in output data")
    val inputData = trailArg[String](descr = "Input data file in ARFF format.")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val inputFile = conf.inputData()
    val name = inputFile.replaceAll("\\.arff(\\.(.*))?$", "")

    //    val data = new DataSet(DataSetLoader.convert(inputFile))
    //    val vocab = data.variables.map(_.getName).toVector

    import Reader._
    
    val instances = Reader.readARFF_native(inputFile)
    val vocab = instances.getAttributes().map(_.name)
    val map = vocab.zipWithIndex.toMap

    exportVocabulary(name, vocab)
    exportData(name, instances.getDataCases(), conf.binaryValue())
  }

  val logger = LoggerFactory.getLogger(this.getClass)

  def exportVocabulary(name: String, vocab: Seq[String]) = {
    val filename = s"${name}.vocab.txt"
    manage(new PrintWriter(filename)) { w =>
      vocab.foreach(w.println)
    }
  }

  def exportData(name: String, instances: Seq[Data.Instance], binarize: Boolean) = {
    val filename = if (binarize) {
      s"${name}.binary.lda.txt"
    } else {
      s"${name}.lda.txt"
    }

    def get(v: Double) = if (binarize) {
      if (v > 0) 1 else 0
    } else
      v.toInt

    manage(new PrintWriter(filename)) { w =>
      instances.foreach { c =>
        if (!isInteger(c.weight))
          logger.warn(s"A data case has a non-integer weight (${c.weight})")

        val values = c.values
        if (!values.forall(isInteger))
          logger.warn("A data case has non-interger values")

        val entries = values.indices
          .filter(i => values(i) > 0)
          .map(i => s"${i}:${get(values(i))}")
        val line = entries.mkString(" ")

        for (i <- 0 until c.weight.toInt) {
          w.print(entries.size)
          w.print(" ")
          w.println(line)
        }
      }
    }
  }

  def isInteger(d: Double) = Math.floor(d) == d
}

object ConvertLDATopicsToList {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner(s"Usage: tm.util.ConvertLDATopicsToList [OPTION]... topic_file")
    val topicFile = trailArg[String](descr = "Topic file resulted from LDA-c.")

    verify
    checkDefaultOpts()
  }

  val NameRegex = raw"(.*?)\._(\w{6})\.topics.txt".r

  def main(args: Array[String]) {
    val conf = new Conf(args)
    val inputName = conf.topicFile()

    val outName = if (inputName.endsWith(".topics.txt")) {
      inputName.replaceAll("topics.txt$", "topic-list.txt")
    } else {
      inputName + ".topic-list.txt"
    }

    val topics = extract(inputName)

    manage(new PrintWriter(outName)) { w =>
      topics.foreach { t =>
        w.println(t.mkString(" "))
      }
    }
  }

  def extract(file: String): Seq[Seq[String]] = {
    manage(Source.fromFile(file)("utf-8")) { s =>
      @tailrec
      def loop(lines: Iterator[String], topics: List[List[String]], current: List[String]): List[List[String]] = {
        if (lines.hasNext) {
          val l = lines.next
          if (l.startsWith("topic "))
            loop(lines, topics, current) // skip
          else if (l.isEmpty()) {
            if (current.isEmpty)
              loop(lines, topics, current) // skip
            else
              loop(lines, current.reverse +: topics, Nil)
          } else {
            val word = l.trim
            loop(lines, topics, word +: current)
          }
        } else
          topics.reverse
      }

      loop(s.getLines, Nil, Nil)
    }
  }
}