package tm.hlta

import org.latlab.model.LTM
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import org.latlab.util.DataSet
import tm.util.Data
import tm.util.Tree
import org.slf4j.LoggerFactory
import tm.util.Reader
import tm.util.Arguments
import tm.util.FileHelpers

object HTD {
  /**
   * Inference, gives a list of document for each topic z
   * 
   * Mathematically, it is finding P(z|d), where z is a topic variable and d is the given document
   * It outputs a list of document d for each z, with P(z|d) > threshold
   * 
   * @return DocumentCatalog
   */
  def buildDocumentCatalog(model: LTM, data: Data, layer: Option[List[Int]] = None, threshold: Double = 0.5, broad: Boolean = false) = {
    import Doc2VecAssignment._
    val binaryData = data.binary()
    //val synchronizedData = binaryData.synchronize(model)//?
    if(broad) 
      Doc2VecAssignment.computeBroadTopicData(model, binaryData, layer).toCatalog(threshold)
    else      
      Doc2VecAssignment.computeNarrowTopicData(model, binaryData, layer).toCatalog(threshold)
  }
  
  /**
   * Inference, assign topic distribution to each document, 
   * Gives a dense non-binary matrix, where each variable is a topic z, each instance is a document d, each cell is P(z|d)
   * 
   * Mathematically, it is finding P(z|d), where z is a topic variable and d is the given document
   * 
   * @return Data
   */
  def computeTopicProbabilities(model: LTM, data: Data, layer: Option[List[Int]] = None, broad: Boolean = false) = {
    val binaryData = data.binary()
    //val synchronizedData = binaryData.synchronize(model)//?
    if(broad) 
      Doc2VecAssignment.computeBroadTopicData(model, binaryData, layer)
    else      
      Doc2VecAssignment.computeNarrowTopicData(model, binaryData, layer)
  }
  
  
  /**
   * Topic keywords extraction, find keywords to characterize each topic
   * 
   * Mathematically, it is finding a list of word w for each z, with maximum MI(w;z)
   * It outputs a topic tree, where each node represents a topic; each topic is characterize with the best keywords
   * 
   * @return TopicTree
   */
  def extractTopicTree(model: LTM, outputName: String, layer: Option[List[Int]] = None, keywords: Int = 7, 
      broad: Boolean = false, data: Data = null, tempDir: String = "./temp") = {  
    if(broad){      
      ExtractTopicTree.broad(model, outputName, layer, keywords, tempDir)
    }else{
      val binaryData = data.binary()
      ExtractTopicTree.narrow(model, binaryData, outputName, layer, keywords, tempDir)
    }
  }
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: tm.hlta.HLTA [OPTION]... data outputName
    |a lazy HLTA call, takes in data file or text, and outputs topic tree and topic assignment(inference)
    |if text is feeded in, it would first convert into data file then do HLTA
    |
    |make sure your data file extension is right
    |text: .txt for plain text, .pdf for pdf files
    |data: .arff for ARFF, .hlcm for HLCM, .sparse.txt for tuple format, .lda.txt for LDA
    |      if LDA, please provide a vocab file using --lda-vocab""")
    
    val data = trailArg[String](descr = "Data, text or folder, auto search for txt and pdf if folder is given, auto convert to data if txt/pdf is feeded")
    val name = trailArg[String](descr = "Output name")
    
    val broad = opt[Boolean](default = Some(false), descr = "use Broad Defined Topic for extraction and assignment, run faster but more document will fall into the topic")
    
    val chinese = opt[Boolean](default = Some(false), 
        descr = "use predefined setting for converting Chinese to data, only valid when conversion is needed")
    val vocabSize = opt[Int](default = Some(1000), descr = "Vocabulary size")
    val topLevelTopics = opt[Int](default = Some(15), descr = "Number of topics on the root level of the topic tree")
        
    val encoding = opt[String](default = Some("UTF-8"), descr = "Input text encoding, default UTF-8")
    val format = opt[String](descr = "Specify input data format if needed, can be \"arff\", \"hlcm\", \"tuple\"")
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val docNames = opt[String](default = None, descr = "Use the provided document name if file name is not available to label documents")
    val docUrls = opt[String](default = None, descr = "Use the provided url if file url is not available")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    //Simple default settings
    //See tm.text.Convert for more options
    val engSettings = tm.text.DataConverter.Settings(concatenations = 1, minCharacters = 3, maxWords = conf.vocabSize(),
        wordSelector = tm.text.WordSelector.ByTfIdf(3, 0, .25), asciiOnly = true, 
        stopWords = null)
    val chiSettings = tm.text.DataConverter.Settings(concatenations = 1, minCharacters = 1, maxWords = conf.vocabSize(),
        wordSelector = tm.text.WordSelector.ByTfIdf(1, 0, .25), asciiOnly = false, 
        stopWords = null)
    implicit val settings = if(conf.chinese()) chiSettings else engSettings

    //Check if path is a dir or a file
    val path = java.nio.file.Paths.get(conf.data())
    val (data, files) = if(java.nio.file.Files.isDirectory(path)){
      val dir = path
      //Look for txt or pdf
      //One file is one document
      val files = FileHelpers.findFiles(dir, List("txt","pdf")).map(dir.resolve)
      if (files.isEmpty) {
        throw new IllegalArgumentException("No txt/pdf files found files under " + dir)
      }
      //Convert raw text/pdf to .sparse.txt format
      val data = tm.text.Convert(conf.name(), paths = files, encoding = conf.encoding())
      (data, files)
    }else if(conf.format.isEmpty && !path.endsWith(".arff") && !path.endsWith(".hlcm") && !path.endsWith(".sparse.txt")){
      //Converts raw text / pdf to .sparse.txt file
      //Here one line is one document
      val data = tm.text.Convert(conf.name(), path = path, encoding = conf.encoding())
      data.saveAsTuple(conf.name()+".sparse.txt")
      (data, null)
    }else{
      val data = tm.util.Reader.readData(path.toString, vocabFile = conf.ldaVocab.getOrElse(""), format = conf.format.toOption)
      (data, null)
    }
    
    val model = HLTA(data.toTupleSparseDataSet(), conf.name(), maxTop = conf.topLevelTopics())
    val topicTree = extractTopicTree(model, conf.name(), broad = conf.broad(), data = data)
    topicTree.saveAsJson(conf.name()+".nodes.json")
    val catalog = buildDocumentCatalog(model, data, broad = conf.broad())
    catalog.saveAsJson(conf.name()+".topics.json")
    //Generate one html file
    topicTree.saveAsHtml(conf.name()+".simple.html")
    
    val docNames = if(files!=null)
      files.map{file => file.getFileName.toString()}
    else if(conf.docNames.isDefined)
      scala.io.Source.fromFile(conf.docNames()).getLines.toList
    else
      (0 until data.size).map("Line"+_)
    
    val docUrls = if(files!=null)
      files.map(_.toString())
    else if(conf.docUrls.isDefined)
      scala.io.Source.fromFile(conf.docUrls()).getLines.toList
    else
      null
      
    //Generate a nice and pretty website, no server required
    tm.hlta.BuildWebsite("./webiste/", conf.name(), conf.name(), topicTree = topicTree, catalog = catalog, docNames = docNames, docUrls = docUrls)
  }
}