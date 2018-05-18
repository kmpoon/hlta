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
import tm.util.Reader
import tm.util.Data

object ExtractTopicTree {
  class Conf(args: Seq[String]) extends Arguments(args) {    
    banner("Usage: tm.hlta.ExtractTopicTree [OPTION]... name model")
    val name = trailArg[String](descr = "Name of files to be generated")
    val model = trailArg[String](descr = "Name of model file (e.g. model.bif)")
    val data = trailArg[String](required = false, descr = "Data file, if using --broad, this is not required")
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val broad = opt[Boolean](default = Some(false), descr = "use broad defined topic, run faster but more document will be categorized into the topic")
    val title = opt[String](default = Some("Topic Tree"), descr = "Title in the topic tree")
    val layer = opt[List[Int]](descr = "Layer number, i.e. --layer 1 3")
    val keywords = opt[Int](default = Some(7), descr = "number of keywords for each topic")
    val tempDir = opt[String](default = Some("topic_output"),
      descr = "Temporary output directory for extracted topic files (default: topic_output)")
      
    verify
    checkDefaultOpts()
    if(data.isEmpty && !broad())
      throw new Exception("Missing parameter data or missing option --broad")
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)
    
    val (model, data) = if(conf.broad())
      Reader.readModelAndData(conf.model(), conf.data(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
    else{
      Reader.readModelAndData(conf.model(), conf.data())
    }
    
    val topicTree = if(conf.broad()) {
      println(s"will broad")
      broad(model, conf.name(), conf.layer.toOption, conf.keywords(), conf.tempDir())
    }
    else {
      println(s"will narrow")
      val binaryData = data.binary()
      narrow(model, binaryData, conf.name(), conf.layer.toOption, conf.keywords(), conf.tempDir())
    }
    println(s"narrow or broad done. will BuildWebsite")
    
    BuildWebsite(".", conf.name(), conf.title(), topicTree)
    topicTree.saveAsJson(conf.name()+".nodes.json")
    println(s"saveAsJson done. filename " + conf.name() + ".nodes.json") 
  }
  
  def broad(model: LTM, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, tempDir: String = "./topic_output") = {
    val output = Paths.get(tempDir)
    FileHelpers.mkdir(output)
    
    val bdtExtractor = new clustering.HLTAOutputTopics_html_Ltm()
    //val param = Array("", tempDir, "no", "no", keywords.toString())
    bdtExtractor.initialize(model, tempDir, false, false, keywords)
    bdtExtractor.run()

    val topicFile = output.resolve("TopicsTable.html")
    val topicTree = TopicTree.readHtml(topicFile.toString())
    //val order = RegenerateHTMLTopicTree.readIslands(FindTopLevelSiblingClusters.getIslandsFileName(conf.name()))
    //topicTree = topicTree.sortRoots { t => order(t.value.name) }
    if(layer.isDefined){
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      topicTree.trimLevels(_layer)
    }else
      topicTree
  }
  
  def narrow(model: LTM, binaryData: Data, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, tempDir: String = "./topic_output") = {
    val output = Paths.get(tempDir)
    FileHelpers.mkdir(output)
    
    val lcmNdtExtractor = new tm.hlta.ExtractNarrowTopics_LCM()
    val param = Array("", "", tempDir, "no", "no", keywords.toString())
    lcmNdtExtractor.initialize(model, binaryData.toHlcmDataSet(), param)
    lcmNdtExtractor.run()
    println(s"narrow lcmNdtExtractor run done")
    val topicFile = output.resolve("TopicsTable.html")
    val topicTree = TopicTree.readHtml(topicFile.toString())
    //val order = RegenerateHTMLTopicTree.readIslands(FindTopLevelSiblingClusters.getIslandsFileName(conf.name()))
    //topicTree = topicTree.sortRoots { t => order(t.value.name) }
    println(s"readHtml done")
    if(layer.isDefined){
      val _layer = layer.get.map{l => if(l<=0) l+model.getHeight-1 else l}
      println(s"isDefined: will trimLevels")
      topicTree.trimLevels(_layer)
    }else{
      println(s"not Defined: will topicTree")
      topicTree
    }
  }
}
