package tm.em

import org.latlab.model.LTM
import org.latlab.util.DataSet
import org.latlab.util.Variable
import org.apache.spark.rdd.RDD
import org.latlab.util.DataSet.DataCase
import org.apache.spark.SparkContext

import collection.JavaConversions._
import org.latlab.model.BayesNet
import org.latlab.model.BeliefNode
import scala.annotation.tailrec
import org.latlab.reasoner.CliqueTreePropagation

object EM {
  /**
   * Configuration for EM
   *
   * @constructor create a new configuration object
   * @param restarts number of restarts
   * @param threshold threshold to control EM convergence
   * @param maxSteps maximum number of steps to control EM convergence
   * @param localMaximaEscapeMethod So far there are two options:
   * "ChickeringHeckerman" and "MultipleRestarts"
   * @param preSteps For "MultipleRestarts" method, the number of preSteps
   * to go in order to choose a good starting point
   * @param initialIterations When using the Chickering-Heckerman method to
   * choose a good starting point, we first generate {@code restarts}
   * random restarts. Then before  eliminating some bad restarts, we run
   * {@code numInitIterations} emStep() for all random restarts
   * @param reuse whether we reuse the parameters of the input BN as a
   * candidate starting point
   */
  case class Conf(
    restarts: Int = 64,
    threshold: Double = 1e-4,
    maxSteps: Int = 500,
    localMaximaEscapeMethod: String = "ChickeringHeckerman",
    preSteps: Int = 10,
    initialIterations: Int = 1,
    reuse: Boolean = true,
    dontUpdateNodes: Set[String] = Set.empty)

  case class Data(source: DataSet,
    variables: Array[Variable], instances: RDD[DataCase])

  def convert(sc: SparkContext, ds: DataSet) =
    Data(ds, ds.getVariables, sc.parallelize(ds.getData))

  def run(model: BayesNet, data: Data, c: Conf) = {
    // selects a good starting point
    val (m, s) = chickeringHeckermanRestart(model, data, c)

    step(m, data, c.dontUpdateNodes)

    repeat(m, data, c.maxSteps - (s + 1),
      c.dontUpdateNodes, Some(c.threshold))._1
  }

  def getLoglikelihood(m: BayesNet, d: Data) = m.getLoglikelihood(d.source)
  def setLogLikelihood(m: BayesNet, d: Data, ll: Double) = m.setLoglikelihood(d.source, ll)

  /**
   * Selects a good starting point using Chickering and Heckerman's strategy.
   * Note that this restarting phase will terminate midway if the maximum
   * number of steps is reached. However, it will not terminate if the EM
   * algorithm already converges on some starting point. That makes things
   * complicated.
   *
   * @param model input BN
   * @param data data set to be used
   * @return the resulting model, loglikelihood, and number of steps taken.
   */
  def chickeringHeckermanRestart(
    model: BayesNet, data: Data, c: Conf): (BayesNet, Int) = {
    var candidates = generateCandidates(model, c.restarts, c.reuse, c.dontUpdateNodes)
    candidates = candidates.map(
      repeat(_, data, c.initialIterations, c.dontUpdateNodes)._1)

    @tailrec
    def select(candidates: Vector[BayesNet],
      stepsPerRound: Int, currentStep: Int): (BayesNet, Int) = {
      if (candidates.size <= 1 || currentStep >= c.maxSteps)
        return (candidates.head, currentStep)

      // repeat EM for a number of steps and sort the results in decreasing
      // order of loglikelihood
      val results = candidates
        .map(repeat(_, data, stepsPerRound, c.dontUpdateNodes, Some(c.threshold)))
        .sortBy(r => -getLoglikelihood(r._1, data))

      val completedSteps = currentStep + results.map(_._2).max

      // if all candidates cannot be improved, return the best one
      if (results.forall(!_._3))
        return (results.head._1, completedSteps)

      select(results.take(results.size / 2).map(_._1),
        stepsPerRound * 2, completedSteps)
    }

    select(candidates, 1, c.initialIterations)
  }

  private[this] def generateCandidates(base: BayesNet,
    restarts: Int, reuse: Boolean, dontUpdateNodes: Set[String]) = {
    val candidates = Vector.fill(restarts)(base.clone)

    def generateParameters(m: BayesNet) {
      if (dontUpdateNodes.isEmpty) m.randomlyParameterize
      else for {
        n <- m.getNodes
        if !dontUpdateNodes.contains(n.getName)
        bn = n.asInstanceOf[BeliefNode]
      } bn.getCpt.randomlyDistribute(bn.getVariable)
    }

    // in case we reuse the parameters of the input BN as a starting
    // point, we put it at the first place.
    (if (reuse) candidates.tail else candidates).foreach(generateParameters)
    candidates
  }

  /**
   * Updates the model parameters with one step of EM.
   */
  def step(model: BayesNet, data: Data, dontUpdateNodes: Set[String]) = {
    val tl = new ThreadLocal[CliqueTreePropagation] {
      override def initialValue() = new CliqueTreePropagation(model)
    }

    val nodes = model.getNodes.toVector
      .filterNot(v => dontUpdateNodes.contains(v.getName))
      .map(_.asInstanceOf[BeliefNode])

    val sl = data.instances.map { i =>
      val ctp = tl.get
      ctp.setEvidence(data.variables, i.getStates)
      ctp.propagate()
      val likelihoodDataCase = ctp.getLastLogLikelihood()
      val stats = nodes.map { n =>
        val fracWeight = ctp.computeFamilyBelief(n.getVariable)
        fracWeight.multiply(i.getWeight)
        fracWeight
      }
      (stats, likelihoodDataCase * i.getWeight)
    }.reduce { // can't assume first element is immutable 
      (p1, p2) =>
        val ss = p1._1.zip(p2._1).map { p =>
          val z = p._1.clone
          z.plus(p._2)
          z
        }
        (ss, p1._2 + p2._2)
    }

    // update parameters
    nodes.zip(sl._1).foreach { p =>
      val (n, s) = p
      val cs = s.getCells
      // Add 1 to each entry to avoid 0 probability By Peixian Chen
      for (i <- 0 until cs.size) cs(i) += 1
      s.normalize(n.getVariable)
      n.setCpt(s)
    }

    setLogLikelihood(model, data, sl._2)
  }

  /**
   * Runs one step of EM and check whether the improvement exceeds the threshold.
   *
   * @return first element is the given model with updated parameters, second
   * element indicates whether the improvement exceeds the given threshold.
   */
  def stepAndCheck(model: BayesNet, data: Data, dontUpdateNodes: Set[String],
    threshold: Option[Double]): (BayesNet, Boolean) = {
    val previous = model.getLoglikelihood(data.source)
    step(model, data, dontUpdateNodes)
    val c = threshold.map(t => previous == Double.NegativeInfinity ||
      getLoglikelihood(model, data) - previous > t)
      .getOrElse(true)

    (model, c)
  }

  /**
   * Repeat EM for a number {@code totalSteps} of steps.  It may stop earlier
   * when a step fails to improve the loglikelihood by the given {@code threshold}.
   *
   * @return first element is the given model with updated parameters,
   * second element is the number of steps run, third element indicates
   * whether the improvement exceeds the given threshold.
   */
  def repeat(model: BayesNet, data: Data,
    totalSteps: Int, dontUpdateNodes: Set[String],
    threshold: Option[Double] = None): (BayesNet, Int, Boolean) = {
    @tailrec
    def loop(m: BayesNet, s: Int, imp: Boolean): (BayesNet, Int, Boolean) = {
      if (s >= totalSteps || !imp) (m, s, imp)
      else {
        val (n, c) = stepAndCheck(m, data, dontUpdateNodes, threshold)
        loop(n, s + 1, c)
      }
    }

    loop(model, 0, true)
  }
}