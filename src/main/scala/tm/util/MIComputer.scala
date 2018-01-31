package tm.util

object NormalizedMutualInformation{
  def apply(counts: Array[Array[Double]]) = MIComputer.compute(counts).normalized
}

object MutualInformation {
  def apply(counts: Array[Array[Double]]) = MIComputer.compute(counts).mi
}

object MIComputer {
  case class MutualInformation(mi: Double, entropies: IndexedSeq[Double]) {
    def normalized = mi / Math.sqrt(entropies.product)
  }

  def compute(counts: Array[Array[Double]]) = {
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

  private def multiply(x1: Double, x2: Double) = if (x1 == 0 || x2 == 0) 0 else x1 * x2

  private def log(x: Double, base: Double = 2) = Math.log(x) / Math.log(base)

  private def getMarginal(counts: Array[Double]) = {
    val sum = counts.sum
    counts.map(_ / sum)
  }

  private def computeEntropy(values: Seq[Double]) = {
    -values.map(v => multiply(v, log(v))).sum
  }

  def main(args: Array[String]){
    println(tm.util.MutualInformation(Array(Array(0, 1), Array(1, 0))))
    println(tm.util.MutualInformation(Array(Array(1, 0), Array(1, 0))))
    println(tm.util.MutualInformation(Array(Array(99, 1), Array(1, 99))))
  }
}