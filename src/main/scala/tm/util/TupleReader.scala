package tm.util

import scala.io.Source
import tm.util.Data.Instance
import org.latlab.util.Variable
import java.util.ArrayList
import weka.core.Attribute
import scala.collection.mutable.MutableList
import scala.collection.mutable.Map

/**
 * Alternative approach use SparseDataSet.java to read then call SparseDataSet.SparseToDense()
 * see org.latlab.learner.SparseDataSet
 * 
 */
object TupleReader {

  def read(filename: String) = {
    val variables = MutableList[String]()    
    val docList = Map[String, MutableList[String]]()

    for (line <- Source.fromFile(filename).getLines) {
      val docId = line.split(",")(0)
      val variable = line.split(",")(1)
      if(!variables.contains(variable))
        variables += variable
      if(docList.get(docId).isEmpty)
        docList += (docId -> MutableList[String]())
      docList.get(docId).get += variable
    }
    
    val instances = docList.map{case (docId, doc) =>
      val indexes = doc.map { word => variables.indexOf(word) }
      val values = (0 until variables.size).map{ i => if(indexes.contains(i)) 1.0 else 0.0}.toArray
      Instance(values, 1.0, name = docId)
    }
    
    val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
    def convert(a: String) = {
      new Variable(a, b)
    }
    
    Data(variables.map(convert(_)).toIndexedSeq, instances.toIndexedSeq, isBinary = true)
  }

}