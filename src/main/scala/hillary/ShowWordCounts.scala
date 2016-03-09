package hillary

import scala.io.Source
import scala.annotation.tailrec

object ShowWordCounts extends App {
    val relationRegex = """(?i)@relation(?:\s+)(\S+)""".r
    val attributeRegex = """(?i)@attribute(?:\s+)(\S+)(?:\s+)(\S+)""".r
    val ignoreRegex = """(//(.*)|(\s*))""".r
    val dataRegex = """(?i)@data(?:\s*)""".r

    if (args.size < 1) {
        println("ShowWordCounts data_file")
    } else {
        run(args(0))
    }

    def run(datafile: String) = {
        println("Reading from: " + datafile)
        val lines = Source.fromFile(datafile).getLines
        val relation = readRelation(lines)
        val attributes = readAttributes(lines)
        val data = readData(lines)
        
        println(showDataCase(data.drop(1).head, attributes))
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

    def readData(lines: Iterator[String]): Seq[Seq[(Int, Int)]] = {
        @tailrec
        def rec(data: List[Seq[(Int, Int)]]): Seq[Seq[(Int, Int)]] = {
            if (lines.hasNext) rec(convert(lines.next) +: data)
            else data
        }

        def convert(line: String): Seq[(Int, Int)] = {
            val values = line.split(",").map(_.toInt)
            for (i <- values.indices if values(i) > 0) yield (i, values(i))
        }

        rec(Nil).reverse
    }

    def showDataCase(c: Seq[(Int, Int)], attributes: IndexedSeq[String]) = {
        c.map { _ match { case (i, v) => s"(${attributes(i)}, $v)" } }
    }
}