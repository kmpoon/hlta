package tm.hlta

import tm.util.Data
import tm.util.Arguments
import org.deeplearning4j.models.word2vec.Word2Vec
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import java.io.File
import tm.util.Reader

object TopicCoherence {
  
  class Conf(args: Array[String]) extends Arguments(args){
    banner(s"Usage: ${TopicCoherence.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... topicFile dataFile")
    val topicFile = trailArg[String](descr = "topic file, in json or html")
    val dataFile = trailArg[String]()
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val m = opt[Int](descr = "numberOfWords", default = Some(4))
    val layer = opt[List[Int]](descr = "select specific level, i.e. 2 3 4", default = None)
    
    verify
    checkDefaultOpts()
  }
  
  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)
    
    val topicTree = if(conf.topicFile().endsWith(".json")) TopicTree.readJson(conf.topicFile()) else TopicTree.readHtml(conf.topicFile())
    val data = Reader.readData(conf.dataFile(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
    val topics = if(conf.layer.isDefined) topicTree.trimLevels(conf.layer()).toList() else topicTree.toList()
    val averageCoherence = averageTopicCoherence(topics, data, conf.m())
    println(averageCoherence)
  }
  
  def apply(topics: Seq[Topic], data: Data, m: Int = 4) = averageTopicCoherence(topics, data, m)
    
  def averageTopicCoherence(topics: Seq[Topic], data: Data, m: Int): Double = {
    val coherences = topics.filter(_.words.size >= m).par.map { x =>  
      topicCoherence(x, data, m)
    }
    coherences.sum/coherences.size
  }
  
  def topicCoherence(topic: Topic, data: Data, m: Int): Double = {
    val combination = topic.words.take(m).toSet.subsets(2)
    combination.map { x =>
      val word = x.toList.map(_.w)
      val coexists = data.df(Seq(word(0), word(1)))
      val exists = data.df(word(0))
      Math.log((coexists+1)/exists)
    }.sum
  }
}

object TopicCompactness {
  
  class Conf(args: Array[String]) extends Arguments(args){
    banner(s"Usage: ${TopicCoherence.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... topicFile dataFile word2vec")
    val topicFile = trailArg[String](descr = "topic file, in json or html")
    val dataFile = trailArg[String]()
    val word2vec = trailArg[String](descr = "pretrained word2vec binary model")
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    val m = opt[Int](descr = "numberOfWords", default = Some(4))
    val layer = opt[List[Int]](descr = "select specific level, i.e. 2 3 4", default = None)
    
    verify
    checkDefaultOpts()
  }
    
  def main(args: Array[String]){
    val conf = new Conf(args)
    
    val topicTree = if(conf.topicFile().endsWith(".json")) TopicTree.readJson(conf.topicFile()) else TopicTree.readHtml(conf.topicFile())
    val data = Reader.readData(conf.dataFile(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
    val gModel = new File(conf.word2vec())
    val w2v = WordVectorSerializer.readWord2VecModel(gModel);
    val topics = if(conf.layer.isDefined) topicTree.trimLevels(conf.layer()).toList() else topicTree.toList()
    val averageCoherence = averageTopicCompactness(topics, data, conf.m(), w2v)
    println(averageCoherence)
    
  }
    
  def averageTopicCompactness(topics: Seq[Topic], data: Data, m: Int, w2v: Word2Vec): Double = {
    val compactnesses = topics.filter(_.words.size >= m).par.flatMap { x =>  
      topicCompactness(x, data, m, w2v)
    }
    compactnesses.sum/compactnesses.size
  }
  
  def topicCompactness(topic: Topic, data: Data, m: Int, w2v: Word2Vec): Option[Double] = {
    val combination = topic.words.take(4).toSet.subsets(2)
    val similarities = combination.toList.flatMap { x => 
      val wordVector = x.toList.map(word => w2v.getWordVector(word.w))
      if(wordVector(0) != null && wordVector(1) != null)
        Some(cosineSimilarity(wordVector(0), wordVector(1)))
      else
        None
    }
    if(similarities.size==0) None
    else Some(similarities.sum/similarities.size)
  }
  
  def cosineSimilarity(vectorA: Array[Double], vectorB: Array[Double]) = {
    var dotProduct = 0.0;
    var normA = 0.0;
    var normB = 0.0;
    for (i <- 0 until vectorA.length) {
        dotProduct += vectorA(i) * vectorB(i)
        normA += Math.pow(vectorA(i), 2)
        normB += Math.pow(vectorB(i), 2)
    }   
    dotProduct / (Math.sqrt(normA * normB))
  }
}

object PerDocumentLoglikelihood{
  class Conf(args: Array[String]) extends Arguments(args){
    banner(s"Usage: ${TopicCoherence.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]...bifFile testsetFile")
    val bifFile = trailArg[String](descr = "HLTM model, in .bif form")
    val testsetFile = trailArg[String](descr = "Testing set")
    
    val ldaVocab = opt[String](default = None, descr = "LDA vocab file, only required if lda data is provided")
    
    verify
    checkDefaultOpts()
  }
  
  /**
   * Copy of function evaluate(model) in PEM.java and StepwiseEMHLTA.java
   */
  import org.latlab.util.ScoreCalculator
  import org.latlab.model.BayesNet
  import org.latlab.util.DataSet
  import org.latlab.model.LTM
  def evaluate(model: LTM, testSet: DataSet) = {
    val Loglikelihood = ScoreCalculator.computeLoglikelihood(model.asInstanceOf[BayesNet], testSet);
	  val perLL = Loglikelihood/testSet.getTotalWeight();
	  perLL
  }
    
  def main(args: Array[String]){
    val conf = new Conf(args)

    val (model, data) = Reader.readModelAndData(conf.bifFile(), conf.testsetFile(), ldaVocabFile = conf.ldaVocab.getOrElse(""))
		 
		val perLL = evaluate(model, data.binary().toHlcmDataSet());
    println("Per-document log-likelihood = "+perLL);    
  }
}