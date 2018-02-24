package tm.util

import java.io.PrintWriter


/**
 * File format that suits David Blei's LDA implementation in C
 * see https://github.com/blei-lab/lda-c
 * 
 * Note that value in LDA format is integer
 */
object LdaWriter {
  
  def writeData(fileName: String, instances: Seq[Data.Instance]) = {
  
    val writer = new PrintWriter(fileName)
  
    instances.foreach { instance =>
      val values = instance.values.zipWithIndex.filter{case(value, index) => value>=0.5}
      writer.print(values.size+" "+values.map{case(value, index) => index+":"+Math.round(value)}.mkString(" "))
    }
  
    writer.close
  }
  
  def writeVocab(fileName: String, variables: Seq[String]) = {
  
    val writer = new PrintWriter(fileName)
  
    variables.foreach { w => writer.println(w) }
  
    writer.close
  }
}