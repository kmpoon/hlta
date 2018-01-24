package tm.util

import java.io.PrintWriter
import tm.util.Data

/**
 * SparseDataSet Writer
 * 
 * see org.latlab.learner.SparseDataSet
 */
object TupleWriter {
  
  implicit final class checkDigit(s:String){
    def isAllDigits() = s forall Character.isDigit
  }

  def write(fileName: String, variables: Seq[String], instances: Seq[Data.Instance]) = {
    
    val writer = new PrintWriter(fileName)
    
    instances.zipWithIndex.foreach{ case (instance, docSeq) =>
      instance.values.zipWithIndex.filter { case (x, wordId) => x>=1.0 }
      .foreach { case (x, wordId) => 
        //Not sure what is the format for .sparse.txt
        //Is user name allowed to be non-integer?
        val docId = if(instance.name.length()>0) instance.name else docSeq.toInt+1
        val word = variables(wordId)
        writer.println(docId+", "+word)
      }
    }
    
//    instances.zipWithIndex.foreach{ case (instance, docSeq) =>
//      instance.values.zipWithIndex.filter { case (x, wordId) => x>=1.0 }
//      .foreach { case (x, wordId) => 
//        val docId = docSeq+1
//        val word = variables(wordId)
//        writer.println(docId+", "+word)
//      }
//    }

    writer.close
  }

}