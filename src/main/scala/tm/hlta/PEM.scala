package tm.hlta

import org.rogach.scallop.ScallopConf
import org.latlab.learner.Parallelism

/**
 * Provides an interface to call PEM with the number of threads as option.
 */
object PEM {
  class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
    val maxThreads = opt[Int](descr =
      "Maximum number of threads to use for parallel computation.  " +
      "The default number is set to the number of CPU cores.  " +
      "If the specified number is larger than the number of CPU cores, the latter number will be used.")
    val others = trailArg[List[String]]()
    verify()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    for (t <- conf.maxThreads)
      Parallelism.instance().setLevel(t)

    clustering.PEM.main(conf.others().toArray);
  }
}