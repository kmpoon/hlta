package tm.hlta

import java.nio.file.Files
import java.nio.file.Paths
import collection.JavaConverters._
import scala.collection.immutable.Set
import scala.io.Source

object Test {
  def main(args: Array[String]) {
//    if (args.length > 0) {
//      val input = this.getClass.getResourceAsStream("/jstree/jstree.js")
//      Files.copy(input, Paths.get(args(0)))
//    } else
//      println("Test file")
    import tm.util.Reader
    import org.latlab.learner.SparseDataSet
    val source = Source.fromFile("./economy.txt")("UTF-8").bufferedReader()
    import scala.util.control.Breaks._
    while(true){
      val line = source.readLine()
      if(line == null)
        break
      else
        println(line)
    }
  }
}