package tm.hlta

import tm.util.Tree
import org.latlab.model.BeliefNode
import java.io.PrintWriter
import tm.util.Reader

object ExportAncestors {
  def main(args: Array[String]) {
    if (args.length < 1)
      printUsage
    else {
      val modelFile = args(0)
      val outputFile =
        if (args.length < 2)
          modelFile.replaceAll(".model.bif", ".acestors.csv")
        else
          args(1)

      run(modelFile, outputFile)
    }
  }

  def printUsage() = {
    println("ExportAncestors model_file [output_file]")
  }

  def run(modelFile: String, outputFile: String) = {
    val model = Reader.readLTM(modelFile)
    val trees = HLTA.buildTopicTree(model)

    def go(tree: Tree[BeliefNode], ancestors: Vector[String],
      results: Vector[Vector[String]]): Vector[Vector[String]] = {
      if (tree.children.isEmpty)
        // ignore leaf nodes
        results
      else {
        val current = ancestors :+ tree.value.getName
        tree.children.foldLeft(results :+ current)((z, x) => go(x, current, z))
      }
    }

    val ancestors = trees.flatMap(go(_, Vector.empty, Vector.empty))

    val writer = new PrintWriter(outputFile)
    writer.println("topic\tancestors")
    ancestors.foreach(as => {
      writer.println(s"${as.last}\t${as.mkString(",")}")
    })
    writer.close
  }
}