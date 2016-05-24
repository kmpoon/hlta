package tm.hlta

import tm.util.Data
import org.latlab.util.Variable
import org.latlab.util.Function
import java.util.ArrayList
import java.util.Arrays
import java.io.PrintWriter

object ComputeMIBetweenYearAndTopic {
  def main(args: Array[String]) {
    if (args.length < 3) {
      printUsage()
    } else {
      val outputFile = if (args.length > 3) Some(args(3)) else None
      run(args(0), args(1), args(2), outputFile)
    }
  }

  case class MutualInformation(mi: Double, entropies: IndexedSeq[Double]) {
    def toCSV(delimiter: String) = (mi +: entropies).mkString(delimiter)
  }

  val yearVariable = new Variable("year",
    new ArrayList(Arrays.asList("2000-03", "2004-07", "2008-11", "2012-15")))

  def printUsage() = {
    println("ComputeMIBetweenYearAndTopic topic_table_file topic_file title_file [output_file]")
    println
    println("e.g. ComputeMIBetweenYearAndTopic papers.TopicsTable.html papers.topics.arff papers.files.txt output.csv")
  }

  def run(topicTableFile: String, topicFile: String, titleFile: String, outputFile: Option[String]) = {
    val topics = TopicTable.read(topicTableFile).map(_ match {
      case (topic, _) => (topic.name, topic)
    }).toMap

    val documentTopics = Reader.readData(topicFile)
    val documents = TitleFile.readDocuments(titleFile).toVector
    val discretizedYears = documents.map(_.year).map(discretize)

    val counts = computeCounts(documentTopics, discretizedYears, 0.5)
    val mi = counts.par.map(computeMI).seq

    val delimiter = "\t"
    val output = documentTopics.variables.zip(mi).map { p =>
      val topic = topics(p._1.getName)
      Seq(topic.name, topic.level, topic.words.mkString(","),
        p._2.toCSV(delimiter)).mkString(delimiter)
    }

    output.foreach(println)

    for (file <- outputFile) {
      val writer = new PrintWriter(file)
      output.foreach(writer.println)
      writer.close
    }
  }

  def discretize(year: Int) = {
    if (year <= 2007)
      0
    else
      1
  }

  def computeCounts(topics: Data, years: Vector[Int], threshold: Double) = {
    val counts = topics.variables.map(v =>
      Array.fill(yearVariable.getStates.size, 2)(0.0))

    val total = topics.instances.map(_.weight).sum

    for {
      (year, instance) <- years.zip(topics.instances)
    } {
      (0 until topics.variables.size).par.foreach { i =>
        val topic = if (instance.values(i) > threshold) 1 else 0
        assert(instance.weight == 1)
        counts(i)(year)(topic) += instance.weight
      }
    }

    counts
  }

  def computeMI(counts: Array[Array[Double]]) = {
    val prob1 = getMarginal(counts.map(_.sum))
    val prob2 = getMarginal(counts.reduce((a1, a2) => a1.zip(a2).map(p => p._1 + p._2)))

    val sum = counts.map(_.sum).sum

    val values = (for {
      i <- (0 until counts.size)
      j <- (0 until counts(i).size)
    } yield {
      val p = counts(i)(j) / sum
      if (p > 0)
        multiply(p, log(p / (prob1(i) * prob2(j))))
      else
        0
    })

    MutualInformation(values.sum,
      IndexedSeq(computeEntropy(prob1), computeEntropy(prob2)))
  }

  def multiply(x1: Double, x2: Double) = if (x1 == 0 || x2 == 0) 0 else x1 * x2
  
  def log(x: Double, base: Double = 2) = Math.log(x) / Math.log(base)

  def getMarginal(counts: Array[Double]) = {
    val sum = counts.sum
    counts.map(_ / sum)
  }

  def computeEntropy(values: Seq[Double]) = {
    -values.map(v => multiply(v, log(v))).sum
  }

}
