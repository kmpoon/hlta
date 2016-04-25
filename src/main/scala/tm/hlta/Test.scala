package tm.hlta

import java.nio.file.Files
import java.nio.file.Paths

object Test {
  def main(args: Array[String]) {
    if (args.length > 0) {
      val input = this.getClass.getResourceAsStream("/jstree/jstree.js")
      Files.copy(input, Paths.get(args(0)))
    } else
      println("Test file")
  }
}