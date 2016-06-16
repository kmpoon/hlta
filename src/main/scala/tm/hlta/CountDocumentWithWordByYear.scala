package tm.hlta

import java.io.PrintWriter

object CountDocumentWithWordByYear {
  def main(args: Array[String]) = {
    if (args.length < 1) printUsage()
    else run(args(0))
  }
  
  def printUsage() = {
    println("CountDocumentWithWordByYear name")
  }
  
  def run(name: String) = {
    import TopicAnalysis._
    
    implicit val c = new Context(name)
    val words = c.data.variables.map(_.getName)
    val years = rangeOfYears.toVector
    val counts = words.view.map(w => (w, countByYear(w, years)))
    
    val writer = new PrintWriter(name + ".docWithWordByYear.csv")
    
    writer.println(("word" +: years.map(_.toString)).mkString(","))
    counts.foreach(_ match {
      case (w, cs) => writer.println((w +: cs.map(_.toString)).mkString(","))
    })
    
    writer.close
  }
}