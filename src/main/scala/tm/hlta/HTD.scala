package tm.hlta

import org.latlab.model.LTM
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import java.io.PrintWriter
import scala.io.Source
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
import tm.text.StanfordNlp

object HTD {
  
  val logger = LoggerFactory.getLogger(HTD.getClass)
  
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
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if input is lda data")

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
      conf.language().toLowerCase() match{
        case "en" | "english" => {
          StanfordNlp.EnglishPreprocessor(text, minChars = minChar, stopwords = StopWords.EnglishStopwords)
          //Without standford nlp
          //val tokens = Preprocessor.EnglishPreprocessor(text, minChars = minChar, stopwords = stopWords)
          //Docuemnt(Sentence(tokens))
        }
        case "zh" | "chinese" => {
          val tokens = Preprocessor.ChinesePreprocessor(text, minChars = minChar, stopwords = StopWords.ChineseStopwords)
          Document(Sentence(tokens))
        }
        case "nonascii" | _ => {
          val tokens = Preprocessor.NonAsciiPreprocessor(text, minChars = minChar, stopwords = StopWords.Empty)
          Document(Sentence(tokens))
        }
      }
    }
    val wordSelector = tm.text.WordSelector.byTfIdf(minChar, 0, .25)

    val path = java.nio.file.Paths.get(conf.data())
    val (data, docNames, docUrls) = {
      if(java.nio.file.Files.isDirectory(path)){
        
        //If path is a dir, look for txt or pdf
        val dir = path
        logger.info("Finding files under {}", dir.toString())
        val files = FileHelpers.findFiles(dir, List("txt","pdf"))
        if(files.isEmpty) throw new IllegalArgumentException("No txt/pdf files found files under " + dir)   
        logger.info("Convert raw text/pdf to .sparse.txt format")
        val data = tm.text.Convert(conf.name(), conf.vocabSize(), paths = files, encoding = conf.encoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat())
        data.saveAsTuple(conf.name()+".sparse.txt") 
        logger.info("Output file reading order")
        val writer = new PrintWriter(s"${conf.name()}.files.txt")
        files.foreach(writer.println)
        writer.close
        
        val docNames = files.map{file => file.getFileName.toString()}
        val docUrls = files.map(_.toString())
        (data, docNames, docUrls)
        
      }else if(List(".arff", ".hlcm", ".sparse.txt", ".lda.txt").exists(path.toString().endsWith(_))){
        
        logger.info("Reading from data file")
        val data = tm.util.Reader.readData(path.toString, ldaVocabFile = conf.ldaVocab.getOrElse(""))
            
        logger.info("Data file does not contains document name nor the original text. No topic details will be shown in the final webpage.")
        val docNames = null
        val docUrls = null
        (data, docNames, docUrls)
        
      }else if(path.toString().endsWith(".txt")){
        
        logger.info("Reading from text file {}", path.toString())
        val data = tm.text.Convert(conf.name(), conf.vocabSize(), path = path, encoding = conf.encoding(), 
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat())
        data.saveAsTuple(conf.name()+".sparse.txt")
        
        val docNames = FileHelpers.using(Source.fromFile(path.toString(), conf.encoding()))(_.getLines().toVector)
        val docUrls = null
        (data, docNames, docUrls)
        
      }else if(path.toString().endsWith(".csv")){
        
        import com.github.tototoshi.csv._
        logger.info("Reading from csv file {}, looking for the field \"text\"", path.toString())
        val data = tm.text.Convert(conf.name(), conf.vocabSize(), path = path, encoding = conf.encoding(), csvField = "text",
            preprocessor = preprocessor, wordSelector = wordSelector, concat = conf.concat())
        data.saveAsTuple(conf.name()+".sparse.txt")
        
        val header = FileHelpers.using(CSVReader.open(path.toString(), conf.encoding()))(_.readNext().getOrElse(List.empty))
        val (hasTitle, hasUrl) = (header.contains("title"), header.contains("url"))
        val docNames = if(hasTitle) FileHelpers.using(CSVReader.open(path.toString(), conf.encoding()))(_.iteratorWithHeaders.map{line => line("title")}.toVector)
                       else FileHelpers.using(Source.fromFile(path.toString(), conf.encoding()))(_.getLines().toVector)
        val docUrls = if(hasUrl) FileHelpers.using(CSVReader.open(path.toString(), conf.encoding()))(_.iteratorWithHeaders.map{line => line("url")}.toVector)
                      else null
        (data, docNames, docUrls)
        
      }else{
        throw new Exception("Unspported file format")
      }
    }
    
    logger.info("Building model")
    val model = HLTA(data, conf.name(), maxTop = conf.topLevelTopics(), globalMaxEpochs = conf.epoch())
    
    logger.info("Extracting topic tree")
    val topicTree = extractTopicTree(model, broad = conf.broad(), data = data, keywords = conf.topicKeywords())
    topicTree.saveAsJson(conf.name()+".nodes.json")
    
    logger.info("Assigning documents with a vector")
    val catalog = buildDocumentCatalog(model, data, broad = conf.broad(), keywords = conf.topicKeywords())
    catalog.saveAsJson(conf.name()+".topics.json")
    
    logger.info("Generating a simple text-only website")
    topicTree.saveAsSimpleHtml(conf.name()+".nodes.simple.html")
      
    logger.info("Generating a nice and pretty website")
    tm.hlta.BuildWebsite(conf.name(), conf.name(), topicTree = topicTree, catalog = catalog, docNames = docNames, docUrls = docUrls)
  }
}