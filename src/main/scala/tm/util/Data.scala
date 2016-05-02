package tm.util

import org.latlab.util.Variable

object Data {
  case class Instance(values: Array[Double], weight: Double)
}

case class Data(variables: IndexedSeq[Variable], instances: Seq[Data.Instance])

