package tm.hlta

import tm.util.Arguments
import java.nio.file.Paths
import java.nio.file.Files
import tm.util.FileHelpers
import tm.util.Tree
import scala.io.Source
import org.latlab.model.LTM
import tm.hlta.HLTA._
import org.latlab.util.DataSet

object ExtractTopics {
  class BaseConf(args: Seq[String]) extends Arguments(args) {    
    val title = opt[String](default = Some("Topic Tree"), descr = "Title in the topic tree")
    val layer = opt[List[Int]](descr = "Layer number, i.e. --layer 1 3")
    val keywords = opt[Int](default = Some(7), descr = "number of keywords for each topic")
    val tempDir = opt[String](default = Some("topic_output"),
      descr = "Temporary output directory for extracted topic files (default: topic_output)")
  }

  class Conf(args: Seq[String]) extends BaseConf(args) {
    banner("Usage: tm.hlta.ExtractTopics [OPTION]... model name")
    
    val model = trailArg[String](descr = "Name of model file (e.g. model.bif)")
    val name = trailArg[String](descr = "Name of files to be generated")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val output = Paths.get(conf.tempDir())
    FileHelpers.mkdir(output)

    clustering.HLTAOutputTopics_html_Ltm.main(
        Array(conf.model(), conf.tempDir(), "no", "no", "7"))

    val topicFile = output.resolve("TopicsTable.html")
    RegenerateHTMLTopicTree.run(topicFile.toString(), conf.name(), conf.title(), conf.layer.toOption)
  }
  
  /**
   * For external call
   */
  def apply(model: LTM, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, outputDir: String = "./temp/") = {
    val output = Paths.get(outputDir)
    FileHelpers.mkdir(output)
    
    val bdtExtractor = new clustering.HLTAOutputTopics_html_Ltm()
    //val param = Array("", outputDir, "no", "no", keywords.toString())
    bdtExtractor.initialize(model, outputDir, false, false, keywords)
    bdtExtractor.run()

    val topicFile = output.resolve("TopicsTable.html")
    val topicTree = TopicTree.readHTML(topicFile.toString())
    if(layer.isDefined){
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      topicTree.trimLevels(_layer)
    }else
      topicTree
  }
}

object ExtractNarrowTopics {
  class Conf(args: Seq[String]) extends ExtractTopics.BaseConf(args) {
    banner("Usage: tm.hlta.ExtractNarrowTopics [OPTION]... model data name")
    
    val model = trailArg[String](descr = "Name of model file (e.g. model.bif)")
    val data = trailArg[String](descr = "Data file (e.g. data.txt)")
    val name = trailArg[String](descr = "Name of files to be generated")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val output = Paths.get(conf.tempDir())
    FileHelpers.mkdir(output)

    tm.hlta.ExtractNarrowTopics_LCM.main(
      Array(conf.model(), conf.data(), conf.tempDir(), "no", "no", "7"));

    val topicFile = output.resolve("TopicsTable.html")
    RegenerateHTMLTopicTree.run(topicFile.toString(), conf.name(), conf.title(), conf.layer.toOption)
  }
  
  /**
   * For external call
   * No IO involved
   */
  def apply(model: LTM, data: DataSet, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, outputDir: String = "./temp/") = {
    val output = Paths.get(outputDir)
    FileHelpers.mkdir(output)
    
    val lcmNdtExtractor = new tm.hlta.ExtractNarrowTopics_LCM()
    val param = Array("", "", outputDir, "no", "no", keywords.toString())
    lcmNdtExtractor.initialize(model, data, param)
    lcmNdtExtractor.run()
    
    val topicFile = output.resolve("TopicsTable.html")
    val topicTree = TopicTree.readHTML(topicFile.toString())
    if(layer.isDefined){
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      topicTree.trimLevels(_layer)
    }else
      topicTree
  }
}