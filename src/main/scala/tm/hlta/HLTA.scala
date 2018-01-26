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
  
  /**
   * Inference, assign topic distribution to each document
   * 
   * Mathematically, it is finding P(z|d), where z is a topic variable and d is the given document
   * It outputs a list of document d for each z, with P(Z|d) > 0.5
   * 
   * @return Assignment
   */
  def assignTopics(model: LTM, data: Data, layer: Option[List[Int]] = None, threshold: Double = 0.5, narrow: Boolean = false) = {
    val binaryData = data.binary()
    val synchronizedData = binaryData.synchronize(model)
    if(narrow) 
      AssignNarrowTopics(model, synchronizedData, layer, threshold)
    else 
      AssignBroadTopics(model, synchronizedData, layer, threshold)
  }
  
  /**
   * Topic keywords extraction, find keywords to characterize each topic
   * 
   * Mathematically, it is finding a list of word w for each z, with maximum MI(w;z)
   * It outputs a topic tree, where each node represents a topic; each topic is characterize with the best keywords
   * 
   * @return TopicTree
   */
  def extractTopics(model: LTM, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, 
      narrow: Boolean = false, data: Data = null, tempDir: String = "./temp") = {  
    if(narrow){      
      ExtractNarrowTopics(model, data.binary().toHlcmDataSet, outputName, layer, keywords)
    }else{
      ExtractTopics(model, outputName, layer, keywords)
    }
  }
  
  /**
   * Model building, build a latent tree model 
   * Default using Stepwise EM, use PEM = true to use Progressive EM
   * 
   * Progressive EM
   * EM parameters:
   * 		emMaxStep: Maximum number of EM steps (e.g. 50).
   * 		emNumRestarts: Number of restarts in EM (e.g. 5).
   * 		emThreshold: Threshold of improvement to stop EM (e.g. 0.01).
   * Model construction parameters:
   * 		udThreshold: The threshold used in unidimensionality test for constructing islands (e.g. 3).
   * 		maxIsland: Maximum number of variables in an island (e.g. 10).
   * 		maxTop: Maximum number of variables in top level (e.g. 15).
   * 
   * Stepwise EM
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
   * Parameter follows the suggested numbers in clustering.PEM and clustering.StepwiseEMHLTA
   * 
   * @return LTM		latent tree model
   */
  def buildModel(data: Data, modelName: String, emMaxStep: Int = 50, emNumRestart: Int = 3, emThreshold: Double = 0.01, 
      udThreshold: Int = 3, maxIsland: Int = 15, maxTop: Int = 30, PEM: Boolean = false,
      globalBatchSize: Int = 500, globalMaxEpochs: Int = 10, globalMaxEmSteps: Int = 100, 
      structBatchSize: Option[Int] = None, firstBatchUseAll: Boolean = false, tempDir: String = "./temp"): LTM = {
    if(!PEM){
      StepwiseEmBuilder(data.toTupleSparseDataSet, modelName, emMaxStep, emNumRestart, emThreshold,
      udThreshold, maxIsland, maxTop, globalBatchSize, globalMaxEpochs, 
      globalMaxEmSteps, structBatchSize, firstBatchUseAll)
          
    }else{      
      ProgressiveEmBuilder(data.binary().toHlcmDataSet, modelName, emMaxStep, emNumRestart, emThreshold, 
      udThreshold, maxIsland, maxTop)
    }
  }
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: tm.hlta.HLTA [OPTION]... data outputName
    |a lazy HLTA call, takes in data file or text, and outputs topic tree and topic assignment(inference)
    |if text is feeded in, it would first convert into data file then do HLTA
    |
    |make sure your data file extension is right
    |text: .txt for plain text, ./dir for dir (can be .txt or .pdf inside)
    |data: .arff for ARFF, .hlcm for HLCM, .sparse.txt for tuple format""")
    
    val data = trailArg[String](descr = "Data file, auto convert to data if text or pdf is feeded (e.g. data.txt)")
    val outputName = trailArg[String](descr = "Output name")
    
    val ndt = opt[Boolean](default = Some(false), descr = "use Narrow Defined Topic for extraction and assignment")
    val pem = opt[Boolean](default = Some(false), descr = "use Progressive EM for parameter estimation")
    val format = opt[String](descr = "Specify data format if extension is not right, can be \"arff\", \"hlcm\", \"tuple\"")
    val chinese = opt[Boolean](default = Some(false), 
        descr = "use predefined setting for converting Chinese to data, only valid when conversion is needed")
    val encoding = opt[String](default = Some("UTF-8"), descr = "Input text encoding, default UTF-8")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    //Simple default settings
    //See tm.text.Convert for more options
    val engSettings = tm.text.Convert.Settings(concatenations = 1, minCharacters = 3,
        wordSelector = tm.text.WordSelector.ByTfIdf(3, 0, .25, 1000), asciiOnly = true, 
        stopWords = null)
    val chiSettings = tm.text.Convert.Settings(concatenations = 1, minCharacters = 1,
        wordSelector = tm.text.WordSelector.ByTfIdf(1, 0, .25, 1000), asciiOnly = false, 
        stopWords = null)
    
    val path = conf.data()
    //Detect if data is converted
    val (data, paths) = if(conf.format.isEmpty && !path.endsWith(".arff") && !path.endsWith(".hlcm") && !path.endsWith(".sparse.txt")){
      implicit val settings = if(conf.chinese()) chiSettings else engSettings
      //Converts a raw text / pdf to .sparse.txt file
      //Allow external edits on paths before passing to BuildWebsite
      val (_data, paths) = tm.text.Convert(conf.outputName(), conf.data(), encoding = conf.encoding())
      _data.saveAsTuple(conf.outputName()+".sparse.txt")
      (_data, paths)
    }else{
      (tm.util.Reader.readData(path, format = conf.format.toOption), null)
    }
    
    val model = buildModel(data, conf.outputName(), PEM = conf.pem())
    val topicTree = extractTopics(model, conf.outputName(), narrow = conf.ndt(), data = data)
    topicTree.saveAsJson(conf.outputName()+".nodes.json")
    val assignment = assignTopics(model, data)
    assignment.saveAsJson(conf.outputName()+".topics.json")
    //Generate one html file
    topicTree.saveAsHtml(conf.outputName()+".simple.html")
    //Generate a nice and pretty website, no server required
    //Use paths as Document names
    tm.hlta.BuildWebsite("./webiste/", conf.outputName(), conf.outputName(), topicTree = topicTree, assignment = assignment, docNames = paths)
  }
}