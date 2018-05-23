package tm.util

import scala.io.Source
import tm.util.Data.SparseInstance
import org.latlab.util.Variable
import java.util.ArrayList

/**
 * File format that suits David Blei's LDA implementation in C
 * see https://github.com/blei-lab/lda-c
 * 
 * Note that value in LDA format is integer
 */
object LdaReader {
  def read(dataFileName: String, vocabFileName: String) = {
    
    val variables = Source.fromFile(vocabFileName).getLines.toIndexedSeq.map{line =>
      val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
      new Variable(line, b)
    }
    
    val instances = Source.fromFile(dataFileName).getLines.zipWithIndex.toIndexedSeq.map{case(line, index) =>
//      val values = Array.fill[Double](variables.size)(0)
//      line.split(" ").drop(1).foreach { pair => 
//        val pos = pair.split(":")(0).toInt
//        val value = pair.split(":")(1).toDouble
//        values(pos) = value
//      }
      val values = line.split(" ").drop(1).map{ pair =>
        (pair.split(":")(0).toInt, pair.split(":")(1).toDouble)
      }.toMap
      new SparseInstance(values, 1.0, name = index.toString())
    }
    var name: Option[String] = None

    new Data(variables, instances)
  }
}