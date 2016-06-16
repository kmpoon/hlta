package tm.util

object PhiCoefficient {
  def compute(counts: Array[Array[Double]]) = {
    val sum1 = counts.map(_.sum)
    val sum2 = counts.reduce(_.zip(_).map(p => p._1 + p._2))
  }
}