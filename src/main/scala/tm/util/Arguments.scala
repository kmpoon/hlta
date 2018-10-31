package tm.util

import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.Exit
import java.util.logging.Logger;
import java.util.logging.Level;

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
    
    if (debug()){
      System.getProperties.setProperty(
        "org.slf4j.simpleLogger.defaultLogLevel", "debug")
      //Pdfbox uses java.util.Logger
      Logger.getLogger("").setLevel(Level.WARNING);
      edu.stanford.nlp.util.logging.RedwoodConfiguration.debugLevel().apply();
    }else{
      //Pdfbox uses java.util.Logger
      Logger.getLogger("").setLevel(Level.OFF);
      //Suppresses stanford nlp unnecessary message
      edu.stanford.nlp.util.logging.RedwoodConfiguration.errorLevel().apply();
    }

    if (showLogTime())
      System.getProperties.setProperty(
        "org.slf4j.simpleLogger.showDateTime", "true")
  }
}