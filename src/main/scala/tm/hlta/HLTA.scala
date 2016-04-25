package tm.hlta

import org.latlab.model.LTM
import scala.annotation.tailrec
import org.latlab.util.Variable
import org.latlab.model.BeliefNode
import scala.collection.JavaConversions._
import org.latlab.util.DataSet
import org.latlab.reasoner.CliqueTreePropagation

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
}