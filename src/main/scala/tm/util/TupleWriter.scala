package tm.util

import java.io.PrintWriter

/**
 * SparseDataSet Writer
 * Note that SparseDataSet is binary in nature
 * 
 * Note that now document index start from 0
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
      instance.values.zipWithIndex.filter { case (x, wordId) => x>=0.5 }
      .foreach { case (x, wordId) => 
        //user-name can be non-integer
        val docId = if(instance.name.length()>0) instance.name else docSeq
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