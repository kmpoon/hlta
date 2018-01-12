package tm.hlta

import tm.hlta.TopicAnalysis.Context
import tm.util.MIComputer
import java.io.PrintWriter
import scala.collection.immutable.TreeMap

object ComputeTopicConferenceAssociation {
  val delimiter = "\t"

  def main(args: Array[String]) = {
    if (args.length < 1) printUsage()
    else run(args(0))
  }

  def printUsage() = {
    println("ComputeTopicConferenceAssociation name")
  }

  def run(name: String) = {
    import TopicAnalysis._

    implicit val c = new Context(name)
    val topics = c.topics.map(_._1)
    val (conferences, conferenceTotals) = getConferenceTotals

    val tables = topics.par.map(t =>
      computeTable(t.name, conferences, conferenceTotals.map(_.toDouble)))
    val values = tables.map(t =>
      (t, MIComputer.compute(t), computePhiCoefficientsForConferences(t)))

    val writer = new PrintWriter(name + ".conference.csv")

    def conferenceHeading(name: String)(c: String) = s"$name (${c})"
    def formatCounts(table: Array[Array[Double]]) = {
      val total = table.reduce(_.zip(_).map(p => p._1 + p._2))
      val counts = table(1)
      val fraction = counts.zip(total).map(p => p._1 / p._2)
      counts ++: fraction ++: Nil
    }

    writer.println(
      ("name" +: "level" +: "words" +: "mi" +: "nmi" +:
        conferences.map(conferenceHeading("phi")) ++:
        conferences.map(conferenceHeading("count")) ++:
        conferences.map(conferenceHeading("fraction")) ++:
        Nil).mkString(delimiter))
    topics.zip(values).foreach(_ match {
      case (t, (table, m, ps)) => writer.println(
        (topicInfo(t) ++: m.mi.toString +: m.normalized
          +: ps.map(_.toString) ++:
          formatCounts(table) ++:
          Nil).mkString(delimiter))
    })

    writer.close
  }

  def getConferenceTotals(implicit c: Context) = {
    val m = TreeMap(c.titles.groupBy(_.conference).mapValues(_.size).toSeq: _*)
    (m.keys.toArray, m.values.toArray)
  }

  def computeTable(topic: String,
    conferences: Array[String], totals: Array[Double])(
      implicit c: Context): Array[Array[Double]] = {
    val map = TopicAnalysis.countByConference(topic).withDefaultValue(0)
    val counts = conferences.map(map.apply).map(_.toDouble)
    Array((totals zip counts).map(p => p._1 - p._2), counts)
  }

  def topicInfo(topic: Topic) = {
    topic.name +: topic.level.toString +: topic.words.mkString(",") +: Nil
  }

  def computePhiCoefficientsForConferences(
    counts: Array[Array[Double]]): IndexedSeq[Double] = {
    // get total with i and the value at i
    def get(i: Int, xs: Array[Double]) =
      Array(xs.sum - xs(i), xs(i)).map(_.toDouble)

    (0 until counts(0).length).map(i => {
      computePhiCoefficients(counts.map(get(i, _)))
    })
  }

  /**
   * Phi Coefficients is defined as:
   *
   * phi = (n11 x n00 - n10 x n01)/(sqrt(n1. x n0. x n.0 x n.1))
   *
   * The n is assumed to be a 2x2 matrix.
   */
  def computePhiCoefficients(n: Array[Array[Double]]): Double = {
    val sum1 = n.map(_.sum)
    val sum2 = n.reduce(_.zip(_).map(p => p._1 + p._2))
    val denominator = Math.sqrt(sum1.product * sum2.product)

    (n(0)(0) * n(1)(1) - n(1)(0) * n(0)(1)) / denominator
  }
}