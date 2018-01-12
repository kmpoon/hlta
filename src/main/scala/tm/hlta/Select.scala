package tm.hlta

import org.slf4j.LoggerFactory
import tm.util.ParMapReduce._
import tm.util.Reader
import org.rogach.scallop._
import tm.util.Arguments

/**
 * To cut out a sub-dataset
 */
object Select {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: tm.text.Select [OPTION]... dataset output words docs
             |e.g. tm.text.Select dataset.sparse.txt subset.sparse.txt animal,baby,carrot 0,2,335,6,45""")
    val dataFile = trailArg[String](descr = "Dataset source")
    val outputName = trailArg[String](descr = "Name of data")
    val words = trailArg[String](descr = "List of words to be kept, eg. animal,baby,carrot")
    val docs = trailArg[String](descr = "List of entry to be kept, eg. 0,2,335,6,45")

    verify
    checkDefaultOpts()
  }
  
  def main(args: Array[String]) {
    val conf = new Conf(args)
    
    val wordList = conf.words().split(",")
    val docList = conf.docs().split(",").map(_.toInt)
    
    run(conf.dataFile(), conf.outputName(), wordList, docList)
  }

  def run(dataFile: String, outputName: String, wordList: Seq[String], docList: Seq[Int]){
    val topicData = Reader.readData(dataFile)

    logger.info("Select a subdataset")
    val variables = topicData.variables.filter(v=>wordList.contains(v.getName)).toIndexedSeq
    val subset = topicData.select(variables, docList)

    logger.info("Saving subdataset")
    subset.saveAsHlcm(outputName)
    
    logger.info("Done")
  }
  
  val logger = LoggerFactory.getLogger(Select.getClass)
}