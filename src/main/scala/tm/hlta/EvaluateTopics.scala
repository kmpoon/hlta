package tm.hlta

import scala.collection.JavaConversions._
import tm.util.Data
import tm.util.Arguments
import com.medallia.word2vec.Word2VecModel
import com.medallia.word2vec.Searcher
import com.medallia.word2vec.Searcher.UnknownWordException
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
    //println("coherences.sum: " + coherences.sum + " coherences.size: " + coherences.size)
    coherences.sum/coherences.size
  }
  
  def topicCoherence(topic: Topic, data: Data, m: Int): Double = {
    val combination = topic.words.take(m).toSet.subsets(2)
    combination.map { x =>
      val word = x.toList.map(_.w)
      val coexists = data.df(Seq(word(0), word(1)))
      val exists = data.df(word(0))
      val smooth_value = 0.00001
      //println("coexists: " + coexists + " exists: " + exists + " Math.log((coexists+1)/exists): " + Math.log((coexists+1)/exists))
      Math.log((coexists+1+smooth_value)/(exists+smooth_value))
    }.sum
  }
}

object TopicCompactness {
  
  class Conf(args: Array[String]) extends Arguments(args){
    banner(s"Usage: ${TopicCompactness.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... topicFile word2vec")
    val topicFile = trailArg[String](descr = "topic file, in json or html")
    val word2vec = trailArg[String](descr = "pretrained word2vec binary model")
    
    val m = opt[Int](descr = "numberOfWords", default = Some(4))
    val layer = opt[List[Int]](descr = "select specific level, i.e. 2 3 4", default = None)
    
    verify
    checkDefaultOpts()
  }
    
  def main(args: Array[String]){
    val conf = new Conf(args)
    
    val topicTree = if(conf.topicFile().endsWith(".json")) TopicTree.readJson(conf.topicFile()) else TopicTree.readHtml(conf.topicFile())
    
    val file = new File(conf.word2vec())
    val w2v = Word2VecModel.fromBinFile(file).forSearch()
    val topics = if(conf.layer.isDefined) topicTree.trimLevels(conf.layer()).toList() else topicTree.toList()
    val averageCoherence = averageTopicCompactness(topics, conf.m(), w2v)
    println(averageCoherence)
  }
    
  def averageTopicCompactness(topics: Seq[Topic], m: Int, w2v: Searcher): Double = {
    val compactnesses = topics.filter(_.words.size >= m).par.flatMap { x =>  
      topicCompactness(x, m, w2v)
    }
    compactnesses.sum/compactnesses.size
  }
  
  def topicCompactness(topic: Topic, m: Int, w2v: Searcher): Option[Double] = {
    val combination = topic.words.take(4).map(_.w).filter(w2v.contains).toSet.subsets(2).toList
    val similarities = combination.map { x => 
      val wordVector = x.toIndexedSeq.map(word => w2v.getRawVector(word))
      cosineSimilarity(wordVector(0), wordVector(1))
    }
    //println(combination)
    //println(similarities + " " + similarities.sum/similarities.size)
    if(similarities.size==0) None
    else Some(similarities.sum/similarities.size)
  }
  
  def cosineSimilarity(vectorA: Seq[java.lang.Float], vectorB: Seq[java.lang.Float]) = {
    var dotProduct = 0.0;
    var normA = 0.0;
    var normB = 0.0;
    for (i <- 0 until vectorA.length) {
        dotProduct += vectorA(i) * vectorB(i)
        normA += vectorA(i) * vectorA(i)
        normB += vectorB(i) * vectorB(i)
    }   
    dotProduct / (Math.sqrt(normA * normB))
  }
}

object PerDocumentLoglikelihood{
  class Conf(args: Array[String]) extends Arguments(args){
    banner(s"Usage: ${PerDocumentLoglikelihood.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]...bifFile testsetFile")
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
