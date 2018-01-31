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
    
    
//    import tm.util.Reader
//    import scala.util.control.Breaks._
//    
//    val data = Reader.readData("./papers9.Z66.sparse.txt")
//    val model = Reader.readModel("./subject.bif")
//    val topicTree = ExtractTopics(model, "bdt", layer = Some(List(1, 3)))
//    val assignment = AssignBroadTopics(model, data, layer = Some(List(1, 3)))
//    BuildWebsite("./", "bdt", "BDT", topicTree = topicTree)
    
    import tm.util.Reader
    val data = Reader.readData("./papers9.Z66.sparse.txt")
    val l = List(382, 414).map(_.toString())
    println(l)
    val data1 = data.subset(l)
    val data2 = data.subset(List(544, 724).map(_.toString()))
    println(data1.size(), data2.size())
  }
}