package tm.util

import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.Exit

class Arguments(args: Seq[String]) extends ScallopConf(args) {
  /**
   * Shows also the help message when error occurs.
   */
  override def onError(e: Throwable): Unit = e match {
    case Exit() => super.onError(e)
    case _ =>
      printHelp()
      println
      super.onError(e)
  }

  val debug = opt[Boolean](descr = "Show debug message")
  val showLogTime = opt[Boolean](descr = "Show time in log", noshort = true)

  def checkDefaultOpts() = {
    if (debug())
      System.getProperties.setProperty(
        "org.slf4j.simpleLogger.defaultLogLevel", "debug")

    if (showLogTime())
      System.getProperties.setProperty(
        "org.slf4j.simpleLogger.showDateTime", "true")
  }
}