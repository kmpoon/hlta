package tm.hlta

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import collection.JavaConversions._

import org.latlab.model.BeliefNode

object GetDescendants {
  def main(args: Array[String]) {
    if (args.size < 2)
      printUsage()
    else
      run(args(0), args(1))
  }

  def printUsage() = {
    println("GetDescendants model_file variable")
  }

  def run(modelFile: String, variableName: String) = {
    val model = Reader.readLTM(modelFile)
    val node = model.getNodeByName(variableName)
    val descendants = getDescendants(node, !_.isLeaf)
    descendants.map(_.getName).foreach(println)
  }

  def getDescendants(node: BeliefNode,
    predicate: (BeliefNode) => Boolean = (_) => true) = {
    @tailrec
    def rec(descendants: List[BeliefNode], nodes: Queue[BeliefNode]): List[BeliefNode] =
      if (nodes.isEmpty)
        descendants.reverse
      else {
        val (n, r) = nodes.dequeue
        val children = n.getChildren.map(_.asInstanceOf[BeliefNode])
        rec(n :: descendants, r ++ children.filter(predicate))
      }

    rec(Nil, Queue(node))
  }
}