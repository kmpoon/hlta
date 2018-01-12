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
import org.slf4j.LoggerFactory
import tm.util.Reader
import tm.util.Arguments

/**
 *  Provides helper methods for HLTA.
 */
object HLTA {
  
  implicit class LTMMethods(model: LTM){
    
    import scala.collection.JavaConversions._
    
    def getVariableLevels() = {
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
    
    def getVariableNameLevels = getVariableLevels.map{case (v, i) => (v.getName, i)}.toMap        
    
    /**
  	 * Returns a level->variable map
  	 * 0 for leaves, tree_height-1 for top level
  	 * Rewritten from Peixian's code
  	 */
  	def getLevelVariables() = {
  	  
  		val levelsToVariables = collection.mutable.Map.empty[Int, Set[Variable]]
  		
  		val internalVar = model.getInternalVars("tree");
  		val leafVar     = model.getLeafVars("tree");
  		
  		levelsToVariables += (0 -> leafVar.toSet)
  		
  		var level=0;
  		while(internalVar.size>0){ 
  			val newSet = collection.mutable.Set.empty[Variable]
  			levelsToVariables.get(level).get.foreach{v =>
  				val parent = model.getNode(v).getParent().getVariable();
  				if(parent != null){
  					internalVar.remove(parent);
  					newSet.add(parent)
  				}
  			}
  			level += 1
  			levelsToVariables += (level -> newSet.toSet)
  		}
  		
  		levelsToVariables.toMap
  	}
    
    def getLevelVariableNames = getLevelVariables.map{case(i, set) => (i, set.map(_.getName))}.toMap
  		  
    
    /**
     * Gets the top level topic variables.
     */
    def getTopLevelVariables(): List[Variable] = {
      val levels = getVariableLevels.toList
        .groupBy(_._2).mapValues(_.map(_._1))
      levels(levels.keys.max)
    }
    
    /**
     * Returns a new subtree root at latent
     */
    def subtreeOf(latent: String) = {
      val clone = model.clone()
      val latentNode = clone.getNodeByName(latent)
//      if(latentNode==null)
//        return None
      val subtreeNodes = latentNode.getDescendants()
      val allNodes = clone.getNodes.toArray
      for(node <- allNodes){
      	if(!subtreeNodes.contains(node) && !node.equals(latentNode)){
  		    val edges = node.asInstanceOf[org.latlab.graph.AbstractNode].getEdges.toArray
  		    for(edge <- edges){
  		      clone.removeEdge(edge.asInstanceOf[org.latlab.graph.Edge])
  		    }
  		    clone.removeNode(node.asInstanceOf[org.latlab.graph.AbstractNode])
        }
      }
      //return Some(clone)
      clone
    }
    
    def getHeight = getLevelVariables.size
  }

  val logger = LoggerFactory.getLogger(HLTA.getClass)

  def hardAssignment(data: DataSet, model: LTM, variables: Array[Variable]) = {

    val tl = new ThreadLocal[CliqueTreePropagation] {
      override def initialValue() = {
        logger.debug("CTP constructed")
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

    val partitions = 1000
    val groups = data.instances.view.grouped(partitions)
    var size = 0

    groups.flatMap { g =>
      val r = g.par.map { d =>
        val ctp = tl.get
        ctp.setEvidence(data.variables.toArray, d.values.map(_.toInt).toArray)
        ctp.propagate()

        val values = variables.map { v => ctp.computeBelief(v).getCells }

        (values, d.weight, d.name)
      }.seq

      size = size + g.size
      logger.info("Finished computing probabilities for {} samples", size)

      r
    }

    //    data.instances.view.par.map { d =>
    //      val ctp = tl.get
    //      ctp.setEvidence(data.variables.toArray, d.values.map(_.toInt).toArray)
    //      ctp.propagate()
    //
    //      val values = variables.map { v => ctp.computeBelief(v).getCells }
    //
    //      (values, d.weight)
    //    }.seq
  }
  
  /**
   * Builds a list of trees of nodes as in a topic tree.  The topic tree is
   * different from the LTM in that each sibling in the topic tree has the same
   * level (distance from observed variables).  The returned tree includes also
   * the observed variables.
   */
  def buildTopicTree(model: LTM): List[Tree[BeliefNode]] = {
    val varToLevel = model.getVariableLevels
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
  
  def assignTopics(model: LTM, data: Data, layer: Option[List[Int]] = None, threshold: Double = 0.5, narrow: Boolean = false) = {
    val binaryData = data.binary()
    val synchronizedData = binaryData.synchronize(model)
    if(narrow) 
      AssignNarrowTopics(model, synchronizedData, layer, threshold)
    else 
      AssignBroadTopics(model, synchronizedData, layer, threshold)
  }
  
//  def extractTopics(model: LTM, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, narrow: Boolean = false, data: Data = null){
//    model.saveAsBif("./temp/temp.bif")
//    if(data!=null)
//      data.saveAsArff("temp", "./temp/temp.arff")
//    extractTopics("./temp/temp.bif", outputName, layer, keywords, narrow, "./temp/temp.arff")
//  }
  
  def extractTopics(modelFile: String, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, narrow: Boolean = false, data: String = null) = {
    if(narrow){
      if(data==null)
        throw new Exception("not enough parameter")
      ExtractNarrowTopics(modelFile, data, outputName, layer, keywords)
    }
    else 
      ExtractTopics(modelFile, outputName, layer, keywords)
  }
  
  /**
   * Only works for arff, hlcm, or tuple(sparse.txt) file format
   */
  def buildModel(dataFile: String, modelName: String, emStep: Int = 50, stepwise: Boolean = true, 
      udThreshold: Int = 3, maxIsland: Int = 10, maxTop: Int = 15, emRestart: Int = 5, emThreshold: Double = 0.01, 
      globalBatchSize: Int = 5000, globalMaxEpoch: Int = 10, globalMaxEmStep: Int = 128, structBatchSize: Int = 8000){
    
    if(stepwise){
      
      val data = Reader.readData(dataFile)
      val tupleFormatFile = if(dataFile.endsWith(".sparse.txt")) dataFile else{
        val filePrefix = dataFile.slice(0, dataFile.lastIndexOf("."))
        val tupleFormatFile = filePrefix + ".sparse.txt"
        data.saveAsTuple(tupleFormatFile)
        tupleFormatFile
      }
      val actualStructBatchSize = if(data.size > structBatchSize) structBatchSize.toString else "all"
      
      clustering.StepwiseEMHLTA.main(Array(tupleFormatFile, emStep.toString, emRestart.toString, emThreshold.toString, 
          udThreshold.toString, modelName, maxIsland.toString, maxTop.toString, 
          globalBatchSize.toString, globalMaxEpoch.toString, globalMaxEmStep.toString, actualStructBatchSize))
          
    }else{
      
      val arffOrHlcmFile = if(dataFile.endsWith(".sparse.txt")){
        val filePrefix = dataFile.slice(0, dataFile.lastIndexOf("."))
        val arffOrHlcmFile = filePrefix + ".arff"
        Reader.readData(dataFile).saveAsArff(filePrefix, arffOrHlcmFile)
        arffOrHlcmFile
      } else dataFile
      
      clustering.PEM.main(Array(dataFile, emStep.toString, emRestart.toString, emThreshold.toString, 
          udThreshold.toString, modelName, maxIsland.toString, maxTop.toString))
    }
  }
  
  def apply(dataFile: String, outputName: String){
    buildModel(dataFile, outputName)
    val modelFile = outputName+".bif"
    extractTopics(modelFile, outputName)
    val (model, data) = tm.util.Reader.readModelAndData(modelFile, dataFile)
    assignTopics(model, data)
  }
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("Usage: tm.hlta.HLTA [OPTION]... data outputName")
    val data = trailArg[String](descr = "Data file (e.g. data.txt)")
    val outputName = trailArg[String](descr = "Output name")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    apply(conf.data(), conf.outputName())
  }
}