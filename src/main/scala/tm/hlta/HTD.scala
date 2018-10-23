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
import tm.text.Preprocessor
import tm.text.Document
import tm.text.Sentence
import tm.text.StopWords

object HTD {
  /**
   * Inference, gives a list of document for each topic z
   * 
   * Mathematically, it is finding P(z|d), where z is a topic variable and d is the given document
   * It outputs a list of document d for each z, with P(z|d) > threshold
   * 
   * @return DocumentCatalog
   */
  def buildDocumentCatalog(model: LTM, data: Data, layer: Option[List[Int]] = None, threshold: Double = 0.5, keywords: Int = 7, broad: Boolean = false) = {
    import Doc2VecAssignment._
    val binaryData = data.binary()
    if(broad) 
      Doc2VecAssignment.computeBroadTopicData(model, binaryData, layer).toCatalog(threshold)
    else      
      Doc2VecAssignment.computeNarrowTopicData(model, binaryData, layer, keywords).toCatalog(threshold)
  }
  
  /**
   * Inference, assign topic distribution to each document, 
   * Gives a dense non-binary matrix, where each variable is a topic z, each instance is a document d, each cell is P(z|d)
   * 
   * Mathematically, it is finding P(z|d), where z is a topic variable and d is the given document
   * 
   * @return Data
   */
  def computeTopicProbabilities(model: LTM, data: Data, layer: Option[List[Int]] = None, keywords: Int = 7, broad: Boolean = false) = {
    val binaryData = data.binary()
    if(broad) 
      Doc2VecAssignment.computeBroadTopicData(model, binaryData, layer)
    else      
      Doc2VecAssignment.computeNarrowTopicData(model, binaryData, layer, keywords)
  }
  
  
  /**
   * Topic keywords extraction, find keywords to characterize each topic
   * 
   * Mathematically, it is finding a list of word w for each z, with maximum MI(w;z)
   * It outputs a topic tree, where each node represents a topic; each topic is characterize with the best keywords
   * 
   * @return TopicTree
   */
  def extractTopicTree(model: LTM, layer: Option[List[Int]] = None, keywords: Int = 7, 
      broad: Boolean = false, data: Data = null, keywordsProb: Boolean = false) = {  
    if(broad){      
      ExtractTopicTree.broad(model, layer, keywords, keywordsProb)
    }else{
      val binaryData = data.binary()
      ExtractTopicTree.narrow(model, binaryData, layer, keywords, keywordsProb)
    }
  }
  
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("""Usage: tm.hlta.HTD [OPTION]... data outputName
    |A lazy all-in-one call that runs through all necessary steps for Hierarchical Topic Detection
    |Takes in data file or text, and outputs topic tree and topic assignment(inference)
    |if text is feeded in, it would first convert into data file then do HLTA
    |
    |make sure your data file extension is right
    |text: .txt for plain text, .pdf for pdf files
    |data: .arff for ARFF, .hlcm for HLCM, .sparse.txt for tuple format, .lda.txt for LDA
    |      if LDA, please provide a vocab file using --lda-vocab""")
    
    val data = trailArg[String](descr = "Data, text or folder. Auto search for txt and pdf if folder is given.")
    val name = trailArg[String](descr = "Output name")
    
    val broad = opt[Boolean](default = Some(false), 
        descr = "use Broad Defined Topic for extraction and assignment, run faster but more document will be categorized into a topic")
    val epoch = opt[Int](default = Some(50), descr = "max number of iterations running through the dataset")
    
    val language = opt[String](default = Some("en"), 
        descr = "Language, default as English, can be {english, chinese, nonascii}")
    val vocabSize = opt[Int](default = Some(1000), descr = "Corpus size (Data conversion option)")
    val concat = opt[Int](default = Some(2), descr = "Word concatenation (Data conversion option)")
    val topLevelTopics = opt[Int](default = Some(15), descr = "Number of topics on the root level of the topic tree")
    val topicKeywords = opt[Int](default = Some(7), descr = "Max number of keywords per topic")
        
    val encoding = opt[String](default = Some("UTF-8"), descr = "Input text encoding, default UTF-8")
    val format = opt[String](descr = "Input format is determined by file ext., specify your own if needed. Can be \"arff\", \"hlcm\", \"tuple\", \"lda\"")
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if input is lda data")
    
    val docNames = opt[String](default = None, descr = "Document names shown on the output webpage. Used when file name is not available.")
    val docUrls = opt[String](default = None, descr = "Document url shown on the output webpage. Used when file url is not available")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    
    val conf = new Conf(args)
    
    val minChar = conf.language().toLowerCase() match{
                    case "en" | "english" => 3
                    case "zh" | "chinese" => 1
                    case "nonascii" | _ => 1
                  }
    def preprocessor(text: String) = {
      val tokens = conf.language().toLowerCase() match{
        case "en" | "english" => Preprocessor.EnglishPreprocessor(text, minChars = minChar, stopwords = StopWords.EnglishStopwords)
        case "zh" | "chinese" => Preprocessor.ChinesePreprocessor(text, minChars = minChar, stopwords = StopWords.ChineseStopwords)
        case "nonascii" | _ => Preprocessor.NonAsciiPreprocessor(text, minChars = minChar, stopwords = StopWords.Empty)
      }
      Document(Sentence(tokens))
    }
    val wordSelector = tm.text.WordSelector.byTfIdf(minChar, 0, .25)

    //Simple default settings
    //See tm.text.DataConvert for more options
//    implicit val settings = tm.text.DataConverter.Settings(concatenations = conf.concat(), 
//        minCharacters = minChar, wordSelector = tm.text.WordSelector.byTfIdf(minChar, 0, .25))

    val path = java.nio.file.Paths.get(conf.data())
    val (data, docNames, docUrls) = {
      if(java.nio.file.Files.isDirectory(path)){
        
        //If path is a dir, look for txt or pdf
        val dir = path
        val files = FileHelpers.findFiles(dir, List())//List("txt","pdf"))
        if(files.isEmpty) throw new IllegalArgumentException("No txt/pdf files found files under " + dir)   
        
        //Convert raw text/pdf to .sparse.txt format
        val data = tm.text.Convert(conf.name(), conf.vocabSize(), paths = files, encoding = conf.encoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat())
        val docNames = files.map{file => file.getFileName.toString()}
        val docUrls = files.map(_.toString())
        (data, docNames, docUrls)
        
      }else if(conf.format.isDefined || List(".arff", ".hlcm", ".sparse.txt", ".lda.txt").exists(path.toString().endsWith(_))){
        //If path is a data file
        val data = tm.util.Reader.readData(path.toString, 
            ldaVocabFile = conf.ldaVocab.getOrElse(""), format = conf.format.toOption)
        val docNames = if(conf.docNames.isDefined) scala.io.Source.fromFile(conf.docNames())(conf.encoding()).getLines.toList 
                       else null
        val docUrls = if(conf.docUrls.isDefined) scala.io.Source.fromFile(conf.docUrls())(conf.encoding()).getLines.toList 
                      else null
        (data, docNames, docUrls)
      }else{
        //If path is not a data file, converts raw text / pdf to .sparse.txt file
        val data = tm.text.Convert(conf.name(), conf.vocabSize(), path = path, encoding = conf.encoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat())
        data.saveAsTuple(conf.name()+".sparse.txt")
        val docNames = if(conf.docNames.isDefined) scala.io.Source.fromFile(conf.docNames())(conf.encoding()).getLines.toList 
                       else scala.io.Source.fromFile(path.toString()).getLines().toVector
                       //If docNames is not given, use doc context as doc label
        val docUrls = if(conf.docUrls.isDefined) scala.io.Source.fromFile(conf.docUrls())(conf.encoding()).getLines.toList 
                      else null
        (data, docNames, docUrls)
      }
    }
    
    val model = HLTA(data, conf.name(), maxTop = conf.topLevelTopics(), globalMaxEpochs = conf.epoch())
    
    val topicTree = extractTopicTree(model, broad = conf.broad(), data = data, keywords = conf.topicKeywords())
    topicTree.saveAsJson(conf.name()+".nodes.json")
    
    val catalog = buildDocumentCatalog(model, data, broad = conf.broad(), keywords = conf.topicKeywords())
    catalog.saveAsJson(conf.name()+".topics.json")
    
    //Generate one html file
    topicTree.saveAsSimpleHtml(conf.name()+".nodes.simple.html")
      
    //Generate a nice and pretty website, no server required
    tm.hlta.BuildWebsite("./", conf.name(), conf.name(), topicTree = topicTree, catalog = catalog, docNames = docNames, docUrls = docUrls)
  }
}