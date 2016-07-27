package tm.hlta

import org.latlab.model.LTM
import scala.annotation.tailrec
import org.latlab.util.Function
import org.latlab.util.Variable
import org.latlab.model.BeliefNode
import scala.collection.JavaConversions._
import org.latlab.util.DataSet
import org.latlab.reasoner.CliqueTreePropagation
import tm.util.Data
import tm.util.Tree

/** 
 *  Provides helper methods for HLTA.
 */
object HLTA {

  def hardAssignment(data: DataSet, model: LTM, variables: Array[Variable]) = {

    val tl = new ThreadLocal[CliqueTreePropagation] {
      override def initialValue() = {
        println("CTP constructed")
        new CliqueTreePropagation(model)
      }
    }

    val instances = data.getData.par.map { d =>
      val ctp = tl.get
      ctp.setEvidence(data.getVariables, d.getStates)
      ctp.propagate()

      val values = variables.map { v =>
        val cells = ctp.computeBelief(v).getCells
        cells.zipWithIndex.maxBy(_._1)._2
      }

      (values, d.getWeight)
    }.seq

    val newData = new DataSet(variables, false)
    instances.foreach { i => newData.addDataCase(i._1, i._2) }

    newData
  }

  /**
   * Returns a sequence of probability sequences where each element of the
   * outer sequence is computed for each data case and the inner sequence is
   * computed for each variable.  Each element of the inner sequence contains
   * also a weight for the corresponding data case.
   */
  def computeProbabilities(model: LTM, data: Data, variables: Seq[Variable]) = {

    val tl = new ThreadLocal[CliqueTreePropagation] {
      override def initialValue() = new CliqueTreePropagation(model)
    }

    data.instances.view.par.map { d =>
      val ctp = tl.get
      ctp.setEvidence(data.variables.toArray, d.values.map(_.toInt).toArray)
      ctp.propagate()

      val values = variables.map { v => ctp.computeBelief(v).getCells }

      (values, d.weight)
    }.seq
  }

  def readLatentVariableLevels(model: LTM) = {
    val variablesToLevels = collection.mutable.Map.empty[Variable, Int]

    def findLevel(node: BeliefNode): Int = {
      val cachedValue = variablesToLevels.get(node.getVariable)
      if (cachedValue.isDefined)
        return cachedValue.get

      // level is zero if this node is a leaf node
      val children = node.getChildren
      if (children.size == 0)
        return 0

      val childLevels = children.map(_.asInstanceOf[BeliefNode]).map(findLevel)
      val level = childLevels.min + 1
      variablesToLevels(node.getVariable) = level
      level
    }

    val root = model.getRoot
    findLevel(root)

    variablesToLevels.toMap
  }

  /**
   * Gets the top level topic variables.
   */
  def getTopLevelVariables(model: LTM): List[Variable] = {
    val levels = HLTA.readLatentVariableLevels(model).toList
      .groupBy(_._2).mapValues(_.map(_._1))
    levels(levels.keys.max)
  }

  /**
   * Builds a list of trees of nodes as in a topic tree.  The topic tree is
   * different from the LTM in that each sibling in the topic tree has the same
   * level (distance from observed variables).  The returned tree includes also
   * the observed variables.
   */
  def buildTopicTree(model: LTM): List[Tree[BeliefNode]] = {
    val varToLevel = HLTA.readLatentVariableLevels(model)
    val levelToVar = varToLevel.toList.groupBy(_._2).mapValues(_.map(_._1))
    val top = levelToVar(levelToVar.keys.max)

    def build(node: BeliefNode): Tree[BeliefNode] = {
      // only latent variable has level, but it may contain observed variable
      if (node.isLeaf)
        Tree.leaf(node)
      else {
        val level = varToLevel(node.getVariable)
        val children = node.getChildren.toList
          .map(_.asInstanceOf[BeliefNode])
          .filter(n => n.isLeaf || varToLevel(n.getVariable) < level)
          .map(build)
        Tree.node(node, children)
      }
    }

    top.map(model.getNode).map(build)
  }
  
  def getValue(f: Function)(variables: Seq[Variable], states: IndexedSeq[Int]) = {
    // from order of function variables to order of given variables
    val indices = f.getVariables.map(variables.indexOf(_))
    f.getValue(indices.map(states.apply).toArray)
  }
}