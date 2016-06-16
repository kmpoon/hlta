package tm.hlta

import org.latlab.model.LTM
import scala.annotation.tailrec
import org.latlab.util.Variable
import org.latlab.model.BeliefNode
import scala.collection.JavaConversions._
import org.latlab.util.DataSet
import org.latlab.reasoner.CliqueTreePropagation
import tm.util.Data

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

  def learnSiblings() = {
    // FastLTA_flat
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
}