package tm.util

import org.latlab.util.Variable
import org.latlab.util.DataSet
import scala.collection.JavaConversions._
import scala.collection.LinearSeq
import java.text.DecimalFormat

object Data {
  case class Instance(values: Array[Double], weight: Double)
}

case class Data(variables: IndexedSeq[Variable],
    instances: IndexedSeq[Data.Instance]) {
  def binary(): Data = {
    def binarize(value: Double) = if (value > 0) 1.0 else 0.0
    copy(instances = instances.map(i =>
      i.copy(values = i.values.map(binarize))))
  }

  def toHLCMData(): DataSet = {
    val data = new DataSet(variables.toArray)

    // map from new index to old index
    val indices = data.getVariables.map(variables.indexOf).toArray

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
}

