package tm.hlta

import java.nio.file.Files
import java.nio.file.Paths

object Test {
  def main(args: Array[String]) {
//    if (args.length > 0) {
//      val input = this.getClass.getResourceAsStream("/jstree/jstree.js")
//      Files.copy(input, Paths.get(args(0)))
//    } else
//      println("Test file")
    import tm.hlta.ExtractTopics
    import tm.hlta.TopicTree._
    val extraction = ExtractTopics("news1kmodel1.bif", "test", Some(List(1, 2)))
    extraction.saveAsJson("test")
  }
}