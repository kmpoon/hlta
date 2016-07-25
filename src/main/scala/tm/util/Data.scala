package tm.util

import org.latlab.util.Variable
import org.latlab.util.DataSet
import scala.collection.JavaConversions._
import scala.collection.LinearSeq
import java.text.DecimalFormat
import java.util.ArrayList

object Data {
  case class Instance(values: Array[Double], weight: Double) {
    def select(indices: Array[Int]) = copy(values = indices.map(values.apply))
  }

  private def binarize(value: Double): Double = if (value > 0) 1.0 else 0.0
  private def binarize(variable: Variable): Variable =
    new Variable(variable.getName, new ArrayList(Seq("s0", "s1")))
}

case class Data(variables: IndexedSeq[Variable],
    instances: IndexedSeq[Data.Instance]) {
  def binary(): Data = {
    copy(
      variables = variables.map(Data.binarize),
      instances = instances.map(i =>
        i.copy(values = i.values.map(Data.binarize))))
  }

  def toHLCMData(): DataSet = {
    val data = new DataSet(variables.toArray)

    // map from new index to old index
    val indices = data.getVariables.map(variables.indexOf(_)).toArray

    instances.foreach { i =>
      val values = indices.map(i.values.apply).map(_.toInt)
      data.addDataCase(values, i.weight)
    }

    data
  }

  def saveAsArff(name: String, filename: String,
    df: DecimalFormat = new DecimalFormat("#0.##")) = {
    ArffWriter.write(name, filename,
      ArffWriter.AttributeType.numeric,
      variables.map(_.getName), instances, df.format)
  }

  def project(vs: IndexedSeq[Variable]) = {
    val indices = vs.map(variables.indexOf(_)).toArray
    Data(vs, instances.map(_.select(indices)))
  }
}

