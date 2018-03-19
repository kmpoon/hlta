package tm.hlta

import org.latlab.model.LTM
import org.latlab.learner.SparseDataSet
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
import org.latlab.learner.Parallelism

object HLTA {
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: HLTA [OPTION]... dataFile emMaxStep name
             |E.g. HLTA data.arff 50 model1
             |The output file will be model1.bif
             |
             |Please refer to the paper "Latent Tree Models for Hierarchical Topic Detection" for algorithmic details""")
             
    val data = trailArg[String]()
    val emMaxStep = trailArg[Int](descr = "Maximum number of EM steps (e.g. 50)")
    val outputName = trailArg[String]()
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val maxThreads = opt[Int](descr =
      "Maximum number of threads to use for parallel computation.  " +
      "The default number is set to the number of CPU cores.  " +
      "If the specified number is larger than the number of CPU cores, the latter number will be used.")
    
    val emNumRestart = opt[Int](descr = "Number of restarts in EM (e.g. 5). <paper session 6.1>", default = Some(5))
    val emThreshold = opt[Double](descr = "Threshold of improvement to stop EM (e.g. 0.01) <paper session 6.1>", default = Some(0.01))
    val udThreshold = opt[Double](descr = "The threshold used in unidimensionality test for constructing islands (e.g. 3). <paper session 5.2>", default = Some(3))
    val maxIsland = opt[Int](descr = "Maximum number of variables in an island (e.g. 10). <paper session 5.1>", default = Some(10))
    val maxTop = opt[Int](descr = "Maximum number of variables in top level (e.g. 15). <paper session 5.1>", default = Some(15))
    
    val globalBatchSize = opt[Int](descr = "Number of data cases used in each stepwise EM step. <paper session 7>", default = Some(1000))
    val globalMaxEpochs = opt[Int](descr = "Number of times the whole training dataset has been gone through (e.g. 10). <paper session 7>", default = Some(10))
    val globalMaxEmSteps = opt[Int](descr = "Maximum number of stepwise EM steps (e.g. 128). <paper session 7>", default = Some(128))
    
    val structBatchSize = opt[Int](descr = "Number of data cases used for building model structure. <paper session 7>", default = None)
    val structUseAll = opt[Boolean](descr = "Use all data cases for building model structure. <paper session 7>", default = Some(false))

    verify
    checkDefaultOpts()
  }
  
  /**
   * Model building, building a hierarchical latent tree
   *    maxThread: Parallemism, how many thread run together
   * Local EM parameters:
   * 		emMaxSteps: Maximum number of EM steps (e.g. 50).
   * 		emNumRestarts: Number of restarts in EM (e.g. 5).
   * 		emThreshold: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
   * 		udThreshold: The threshold used in unidimensionality test for constructing islands (e.g. 3).
   * 		maxIsland: Maximum number of variables in an island (e.g. 10).
   * 		maxTop: Maximum number of variables in top level (e.g. 15).
   * Global parameters:
   * 		globalBatchSize: Number of data cases used in each stepwise EM step (e.g. 1000).
   * 		globalMaxEpochs: Number of times the whole training dataset has been gone through (e.g. 10).
   * 		globalMaxEmSteps: Maximum number of stepwise EM steps (e.g. 128).
   * 		structBatchSize: Number of data cases used for building model structure.
   * 
   * Parameter follows the suggested numbers in cluster.StepwiseEMHLTA
   */
  def apply(data: Data, modelName: String, ldaVocab: String = null, maxThread: Option[Int] = None,
      emMaxStep: Int = 50, emNumRestart: Int = 3, emThreshold: Double = 0.01,
      udThreshold: Int = 3, maxIsland: Int = 15, maxTop: Int = 30, 
      globalBatchSize: Int = 500, globalMaxEpochs: Int = 10, globalMaxEmSteps: Int = 100, 
      structBatchSize: Option[Int] = None, structBatchAll: Boolean = false): LTM = {
    
    val _sizeFirstBatch = if(structBatchAll) "all"
      else if(structBatchSize.isEmpty){//auto determine structBatchSize
        if(data.size > 10000) 5000.toString() else "all"
      }else{          
        structBatchSize.toString()
      }
    if(maxThread.isDefined)
      Parallelism.instance().setLevel(maxThread.get)
      
    val builder = new clustering.StepwiseEMHLTA()
    builder.initialize(data.toTupleSparseDataSet(), emMaxStep, emNumRestart, emThreshold, udThreshold, modelName, maxIsland, maxTop, globalBatchSize, globalMaxEpochs,
          globalMaxEmSteps, _sizeFirstBatch)
    builder.IntegratedLearn()
      
    Reader.readModel(modelName+".bif")
  }
  
  def main(args: Array[String]){
    val conf = new Conf(args)
    
    val data = Reader.readData(conf.data(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
    
    val _sizeFirstBatch = if(conf.structUseAll()) "all"
      else if(conf.structBatchSize.isEmpty){//auto determine structBatchSize
        if(data.size > 10000) 5000.toString() else "all"
      }else{          
        conf.structBatchSize().toString()
      }
      
    val builder = new clustering.StepwiseEMHLTA()
    builder.initialize(data.toTupleSparseDataSet(), conf.emMaxStep(), conf.emNumRestart(), conf.emThreshold(), conf.udThreshold(), conf.outputName(), 
        conf.maxIsland(), conf.maxTop(), conf.globalBatchSize(), conf.globalMaxEpochs(), conf.globalMaxEmSteps(), _sizeFirstBatch)
    builder.IntegratedLearn()
  }
  
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
        if (children.size == 0){
          variablesToLevels(node.getVariable) = 0
          return 0
        }
  
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
    
    def allWordsOf(root: String) : List[Variable] = {
      val subtopics = scala.collection.mutable.ListBuffer[Variable]()
      foreachSubtreeNodeOf(root, bNode => if(bNode.isLeaf) subtopics+=bNode.getVariable)
      subtopics.toList
    }
    
    def subtopicsOf(root: String) : List[Variable] = {
      val subtopics = scala.collection.mutable.ListBuffer[Variable]()
      foreachSubtreeNodeOf(root, bNode => if(!bNode.isLeaf) subtopics+=bNode.getVariable)
      subtopics.toList
    }
    
    def subtopicsAndWordsOf(root: String) : List[Variable] = {
      val subtopics = scala.collection.mutable.ListBuffer[Variable]()
      foreachSubtreeNodeOf(root, bNode => subtopics+=bNode.getVariable)
      subtopics.toList
    }
    
    def foreachSubtreeNodeOf(root: String, f: BeliefNode => Unit){
      //Note descendants !== children and (grand)+ children
      //See LTM papers, variable on the same level can root to each other
      //Here we write our own DFS search
      val variableNameLevels = model.getVariableNameLevels
      val rootNode = model.getNodeByName(root)
      val rootLevel = variableNameLevels.get(root).get
      
      def _subtopicsOf(node: BeliefNode){
        if(variableNameLevels.get(node.getName).get < rootLevel){
          f(node)
          node.getChildren.map{child => _subtopicsOf(child.asInstanceOf[BeliefNode])}
        }
      }
      rootNode.getChildren.map{child => _subtopicsOf(child.asInstanceOf[BeliefNode])}
    }
    
    /**
     * Returns a new subtree root at latent
     */
    def subtreeOf(newRoot: String): LTM = {
      val clone = model.clone()
      val variableLevels = clone.getVariableNameLevels
      val newRootNode = clone.getNodeByName(newRoot)
      val newRootLevel = variableLevels.get(newRoot).get
      assert(newRootNode!=null)
      
      val subTreeNodes = clone.subtopicsAndWordsOf(newRoot)
      
      val allNodes = clone.getNodes.toArray
      for(node <- allNodes){
        val nodeLevel = variableLevels.get(node.asInstanceOf[BeliefNode].getName).get
        //Keep children and (grand)+ childrens and the root itself
      	if(!subTreeNodes.contains(node.asInstanceOf[BeliefNode].getVariable) && !node.equals(newRootNode)){
  		    val edges = node.asInstanceOf[BeliefNode].getEdges.toArray
  		    for(edge <- edges){
  		      clone.removeEdge(edge.asInstanceOf[org.latlab.graph.Edge])
  		    }
  		    clone.removeNode(node.asInstanceOf[BeliefNode])
        }
      }
      return clone
    }
    
    /**
     * Height of LTM, this includes the leaves (observed variables)
     */
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
}