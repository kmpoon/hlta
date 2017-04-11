package tm.util

import scala.io.Source
import scala.annotation.tailrec

/**
 * Show non-zero values in an ARFF file.
 */
object ShowEntryInArff extends App {
  type SparseInstance = Seq[(Int, Double)]

  val relationRegex = """(?i)@relation(?:\s+)(\S+)""".r
  val attributeRegex = """(?i)@attribute(?:\s+)(\S+)(?:\s+)(\S+)""".r
  val ignoreRegex = """(//(.*)|(\s*))""".r
  val dataRegex = """(?i)@data(?:\s*)""".r

  if (args.size < 1) {
    println("ShowEntryInArff data_file [row_number ...]")
  } else {
    val rows = if (args.size > 1) Some(args.drop(1).toSeq.map(_.toInt)) else None
    run(args(0), rows)
  }

  def run(datafile: String, rows: Option[Seq[Int]]) = {
    println("Reading from: " + datafile)
    val lines = Source.fromFile(datafile).getLines
    val relation = readRelation(lines)
    val attributes = readAttributes(lines)
    val data = readData(lines)

    rows.map(_.map(data.apply)).getOrElse(data).foreach { r =>
      println(showDataCase(r, attributes))
    }
  }

  def readRelation(lines: Iterator[String]) = {
    @tailrec
    def rec(): Option[String] = {
      if (lines.hasNext) {
        lines.next match {
          case relationRegex(name) => Some(name)
          case _ => rec()
        }
      } else
        None
    }

    rec()
  }

  def readAttributes(lines: Iterator[String]): IndexedSeq[String] = {
    @tailrec
    def rec(attributes: Seq[String]): Seq[String] = {
      if (lines.hasNext) {
        lines.next match {
          case attributeRegex(name, kind) => rec(name +: attributes)
          case dataRegex() => attributes
          case _ => rec(attributes)
        }
      } else
        attributes
    }

    rec(Nil).toVector.reverse
  }

  def readData(lines: Iterator[String]): Seq[SparseInstance] = {
    @tailrec
    def rec(data: List[SparseInstance]): Seq[SparseInstance] = {
      if (lines.hasNext) rec(convert(lines.next) +: data)
      else data
    }

    def convert(line: String): SparseInstance = {
      val values = line.split(",").map(_.toDouble)
      for (i <- values.indices if values(i) > 0) yield (i, values(i))
    }

    rec(Nil).reverse
  }

  def showDataCase(c: SparseInstance, attributes: IndexedSeq[String]) = {
    c.map { _ match { case (i, v) => s"${attributes(i)}: $v" } }.mkString(", ")
  }
}