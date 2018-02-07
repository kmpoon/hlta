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
    data.saveAsHlcm("papers9.Z66.hlcm")
  }
}