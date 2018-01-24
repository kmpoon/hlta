package tm.hlta

import org.latlab.model.LTM
import org.latlab.reasoner.CliqueTreePropagation
import org.latlab.util.Variable
import org.latlab.model.BeliefNode
import org.latlab.util.Function
import tm.util.Tree
import collection.JavaConversions._
import tm.util.Data
import org.latlab.util.DataSet
import org.latlab.learner.ParallelEmLearner
import scala.collection.LinearSeq
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import java.util.ArrayList
import java.text.DecimalFormat
import tm.util.MIComputer
import scala.language.reflectiveCalls
import tm.util.Reader
import tm.util.TreeList

object FindNarrowlyDefinedTopics {
  /**
   *  Holds a topic variable and a list of document indices that belong to that
   *  topic.  The indices should be sorted in ascending order.
   */
  type Column = (Variable, List[Int])
  val MAX_NUMBER_OF_WORDS = 7

  def main(args: Array[String]) {
    if (args.length < 1) printUsage
    else {
      val (modelFile, dataFile) =
        if (args.length == 1) (args(0) + ".model.bif", args(0) + ".data.arff")
        else (args(0), args(1))

      run(modelFile, dataFile)
    }
  }

  case class Context(marginals: Map[Variable, Function], data: Data, hlcmData: DataSet)

  def printUsage() = {
    println("FindNarrowlyDefinedTopics model_file data_file")
    println("FindNarrowlyDefinedTopics name")
    println
    println("e.g. FindNarrowlyDefinedTopics model.bif data.arff")
    println("If only one name is given, the model file is assumed " +
      "to be name.model.bif and data file is assumed to be name.data.arff")
  }

  def run(modelFile: String, dataFile: String) = {
    val (model, data, hlcmData) = readModelAndData(modelFile, dataFile)
    val trees = HLTA.buildTopicTree(model)

    println("Computing marginals of latent variables")
    val marginals = computeMarginals(model)

    println("Processing trees")
    implicit val c = Context(marginals, data.binary(), hlcmData)
    val assignments = trees.map(processSubTree)

    println("Saving topic assignments")
    val name = dataFile.replaceAll(".data.arff", ".ndt")
    save(name, name + ".topics.arff", assignments, data.instances.map(_.weight))

    println("Computing topic information")
    val topics = computeTopicInformation(assignments, model, data)

    println("Generating JSON file and HTML topic tree")
    topics.saveAsJs(name + ".nodes.js")
    BuildWebsite.writeHtmlOutput(name, name, name + ".html")
    //    save(name + ".node.js", topics)
  }

  def readModelAndData(modelFile: String, dataFile: String) = {
    val (model, data) = Reader.readLTMAndARFF(modelFile, dataFile)
    val binaryData = data.binary
    val hlcmData = data.toHLCMDataSet()
    (model, binaryData, hlcmData)
  }

  /**
   * Computes the marginal probability of each latent variable.
   */
  def computeMarginals(model: LTM): Map[Variable, Function] = {
    val ctp = new CliqueTreePropagation(model)
    ctp.propagate
    model.getInternalVars.map(v => (v, ctp.computeBelief(v))).toMap
  }

  def projectData(tree: Tree[BeliefNode], data: Data, next: List[Column]): List[Column] = {
    val childVariables = tree.children.map(_.value.getVariable)

    // if children are observed variables, construct data from the original data
    // otherwise construct data from next level assignment
    if (tree.children.head.value.isLeaf)
      childVariables.map(getColumn(data))
    else {
      // reorder the next level columns
      val m = next.toMap[Variable, List[Int]]
      childVariables.map(v => (v, m(v)))
    }
  }

  def getColumn(data: Data)(variable: Variable): Column = {
    val index = data.variables.indexOf(variable)
    val instances = data.instances.toVector
    val list = (0 until instances.size)
      .filter(d => instances(d).values(index) > 0).toList
    (variable, list)
  }

  /**
   * Processes subtree of topic variables (latent variables).
   */
  def processSubTree(tree: Tree[BeliefNode])(implicit c: Context): Tree[Column] = {
    val children = tree.children.filterNot(_.value.isLeaf).par.map(processSubTree).toList

    val assignment = processRootOfSubTree(
      tree, projectData(tree, c.data, children.map(_.value)))
    Tree.node(assignment, children.toSeq)
  }

  def processRootOfSubTree(
    tree: Tree[BeliefNode], lowerLevel: List[Column])(implicit c: Context): Column = {
    val variable = tree.value.getVariable
    var lcm = extractLCM(tree, c.marginals(variable))
    val baseData = convertToData(
      lowerLevel, c.data.instances.map(_.weight))
    lcm = estimate(variable, lcm, baseData.toHLCMDataSet())
    lcm = reorder(variable, lcm)
    assign(variable, lcm, baseData)
  }

  def estimate(root: Variable, model: LTM, data: DataSet): LTM = {
    // the model is irregular is it has fewer than 3 leaf nodes
    if (model.getLeafVars.size < 3) {
      estimateByCounting(root, model, data)
    } else {
      runEM(model, data, 100, 64)
    }
  }

  def toArrayList[A](as: A*): ArrayList[A] = new ArrayList(as)

  def estimateByCounting(root: Variable, model: LTM, data: DataSet): LTM = {
    val m = model.clone
    val rootNode = m.getNode(root)
    val leafNodes = rootNode.getChildren.map(_.asInstanceOf[BeliefNode])

    val projected = data.project(new ArrayList(leafNodes.map(_.getVariable)))

    def sumWeights[T <: { def getWeight(): Double }](ds: Seq[T]) =
      ds.map(_.getWeight).sum

    val (zeros, others) = projected.getData.partition(_.getStates.forall(_ == 0))
    val numberOfZeros = sumWeights(zeros)

    rootNode.getCpt.getCells()(0) = numberOfZeros
    rootNode.getCpt.getCells()(1) = sumWeights(others)
    rootNode.getCpt.normalize()

    leafNodes.foreach { l =>
      val i = projected.getVariables.indexOf(l.getVariable)
      val vs = new ArrayList(Seq(root, l.getVariable))
      val cpt = Function.createFunction(vs)
      cpt.setCell(vs, new ArrayList(Seq[Integer](0, 0)), numberOfZeros)
      cpt.setCell(vs, new ArrayList(Seq[Integer](0, 1)), 0)

      val (d0, d1) = others.partition(_.getStates()(i) == 0)
      cpt.setCell(vs, new ArrayList(Seq[Integer](1, 0)), sumWeights(d0))
      cpt.setCell(vs, new ArrayList(Seq[Integer](1, 1)), sumWeights(d1))
      cpt.normalize(l.getVariable)
      
      l.setCpt(cpt)
    }

    m
  }

  def runEM(model: LTM, data: DataSet, maxSteps: Int, restarts: Int): LTM = {
    val l = new ParallelEmLearner()
    l.setLocalMaximaEscapeMethod("ChickeringHeckerman")
    l.setMaxNumberOfSteps(maxSteps)
    l.setNumberOfRestarts(restarts)
    l.setReuseFlag(false)
    l.setThreshold(0.01)

    l.em(model, data).asInstanceOf[LTM]
  }

  def convertToData(columns: List[Column], weights: IndexedSeq[Double]): Data = {
    @tailrec
    def loop(i: Int, instances: Vector[Data.Instance],
      columns: Array[List[Int]]): IndexedSeq[Data.Instance] = {
      if (i >= weights.length) return instances

      val heads = columns.map(_.headOption.getOrElse(weights.size))
      val next = heads.min

      def zeros = Array.fill(columns.length)(0.0)

      if (i == next) {
        // generate an instance with non-zero elements 
        val minIndices = heads.zipWithIndex.filter(_._1 == next).map(_._2)
        val values = zeros
        minIndices.foreach(values(_) = 1)
        val newColumns = minIndices.foldLeft(columns)((cs, i) => cs.updated(i, cs(i).tail))

        loop(i + 1, instances :+ Data.Instance(values, weights(i)), newColumns)
      } else {
        // generate instances with all zero elements and skip to the next
        // instance with non-zero element
        val allZero = for (j <- (i until next))
          yield Data.Instance(zeros, weights(j))
        loop(next, instances ++ allZero, columns)
      }
    }

    new Data(columns.map(_._1).toIndexedSeq,
      loop(0, Vector.empty, columns.map(_._2).toArray))
  }

  def buildLCM(parent: (Variable, Option[Function]),
    children: Seq[(Variable, Option[Function])]): LTM = {
    val m = new LTM
    val root = m.addNode(parent._1)
    parent._2.foreach(root.setCpt)

    children.foreach { c =>
      val cn = m.addNode(c._1)
      m.addEdge(cn, root)
      c._2.map(_.clone).foreach(cn.setCpt)
    }

    m
  }

  def extractLCM(tree: Tree[BeliefNode], marginal: Function): LTM = {
    def convert(node: BeliefNode): (Variable, Option[Function]) =
      (node.getVariable, Some(node.getCpt))

    buildLCM((tree.value.getVariable, Some(marginal)),
      tree.children.map(c => convert(c.value)))
  }

  def reorder(v: Variable, m: LTM): LTM = {
    val model = m.clone

    val node = model.getNode(v)
    val children = node.getChildren.map(_.asInstanceOf[BeliefNode])
    val sums = (0 until v.getCardinality).map { i =>
      val s = children.map { c =>
        HLTA.getValue(c.getCpt)(IndexedSeq(v, c.getVariable), Array(i, 1))
      }.sum

      (i, s)
    }

    val order = sums.sortBy(_._2).map(_._1)
    node.reorderStates(order.toArray)

    model
  }

  def assign(variable: Variable, model: LTM, data: Data): Column = {
    val ctp = new CliqueTreePropagation(model)

    // grouping instances with same value to reduce computation
    val list = data.instances.zipWithIndex.groupBy(_._1.values.map(_.toInt)).toList
      .map { p =>
        ctp.setEvidence(data.variables.toArray, p._1)
        ctp.propagate
        // return the state that has the highest probability
        val s = ctp.computeBelief(variable).getCells.zipWithIndex.maxBy(_._1)._2
        (s, p._2)
      }.filter(_._1 > 0)
      .flatMap(_._2.map(_._2))
      .sorted

    (variable, list)
  }

  def save(name: String, filename: String,
    columns: List[Tree[Column]], weights: IndexedSeq[Double]) = {
    val data = convertToData(columns.flatMap(_.toList), weights)
    data.saveAsArff(filename, new DecimalFormat("#0.##"))
  }

  def computeTopicInformation(
    assignments: Seq[Tree[Column]], model: LTM, data: Data): TopicTree = {
    // use the tree to find children since the tree does not contain observed
    // variables
    def go(tree: Tree[Column]): (Tree[Topic], List[Variable], Int) = {
      val childResults = tree.children.map(go)

      val leaves = if (childResults.isEmpty) {
        model.getNode(tree.value._1).getChildrenVars.toList
      } else {
        childResults.flatMap(_._2)
      }

      val level = if (childResults.isEmpty) 1 else childResults.map(_._3).max + 1

      val topic = computeTopic(tree.value, level, leaves, data)
      (Tree.node(topic, childResults.map(_._1)), leaves, level)
    }

    TopicTree(assignments.map(go).map(_._1))
  }

  def computeTopic(column: Column, level: Int, leaves: List[Variable], data: Data): Topic = {
    val weights = data.instances.map(_.weight)
    val topicSize = column._2.map(weights.apply).sum / weights.sum

    val columnValues = convertSparseToDense(column._2, data.instances.size)
    val info = leaves.map(l => {
      val index = data.variables.indexOf(l)
      val values = data.instances.map(_.values(index))
      val info = computeWordInfo(columnValues, values, weights)
      (l.getName, info)
    }).sortBy(-_._2.mi)
    val words = info.take(MAX_NUMBER_OF_WORDS)
      .map(_ match { case (w, WordInfo(_, p)) => Word(w, p) })

    Topic(column._1.getName, level, topicSize, None, words)
  }

  def convertSparseToDense(list: List[Int], size: Int) = {
    val array = Array.fill(size)(0.0)
    list.foreach { array(_) = 1 }
    array
  }

  case class WordInfo(mi: Double, conditionalProbability: Double)

  /**
   * Returns the MI and the conditional probability
   * (given state 1 of variable corresponding to c1).
   */
  def computeWordInfo(c1: Array[Double], c2: IndexedSeq[Double],
    ws: IndexedSeq[Double]): WordInfo = {
    val counts = Array.fill(2)(Array.fill(2)(0.0))
    (0 until ws.size).foreach(i => counts(c1(i).toInt)(c2(i).toInt) += ws(i))
    counts.map(_.map(_ / ws.sum))

    WordInfo(MIComputer.compute(counts).mi, counts(1)(1) / counts(1).sum)
  }

  def save(filename: String, topics: Seq[Tree[Topic]]) = ???

}