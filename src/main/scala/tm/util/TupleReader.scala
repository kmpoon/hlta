package tm.util

import scala.io.Source
import tm.util.Data.SparseInstance
import org.latlab.util.Variable
import java.util.ArrayList
import scala.collection.mutable.Map
import scala.collection.mutable.MutableList

/**
 * Alternative approach use SparseDataSet.java to read then call SparseDataSet.SparseToDense()
 * see org.latlab.learner.SparseDataSet
 */
object TupleReader {

  def read(filename: String) = {
    val variables = Map[String,Int]()
    val instances = Map[String,SparseInstance]()
    val order = MutableList[String]()

    val reader = Source.fromFile(filename)
    var currDocId = ""
    var firstLine = true
    val currDocVars = Map[Int,Int]()
    reader.getLines().foreach{ line =>
      val docId = line.split(",")(0)
      val variable = line.split(",")(1)
      val varId = variables.getOrElseUpdate(variable, variables.size)
      if(firstLine){
        currDocId = docId
        firstLine = false
      }
      if(currDocId!=docId){
        val sparseValues = if(instances.contains(currDocId)){
          currDocVars.mapValues(_.toDouble).++:(instances(currDocId).sparseValues).toMap
        }else{
          order += currDocId
          currDocVars.mapValues(_.toDouble).toMap
        }
        instances += (currDocId -> new SparseInstance(sparseValues = sparseValues, 1.0, name = currDocId.toString))
        currDocVars.clear()
        currDocId = docId
      }
      currDocVars.update(varId, 1)
    }
    val sparseValues = if(instances.contains(currDocId)){
      currDocVars.mapValues(_.toDouble).++:(instances(currDocId).sparseValues).toMap
    }else{
      order += currDocId
      currDocVars.mapValues(_.toDouble).toMap
    }
    instances += (currDocId -> new SparseInstance(sparseValues = sparseValues, 1.0, name = currDocId.toString))
    reader.close()
    
    def convert(a: String) = {
      val b = new ArrayList[String]()
      b.add(0, "s0")
      b.add(1, "s1")
      new Variable(a, b)
    }
    
    new Data(variables.toIndexedSeq.sortBy(_._2).map(_._1).map(convert), order.map(instances).toIndexedSeq, isBinary = true)
  }

}