package tm.hlta

import tm.util.Data
import org.deeplearning4j.models.word2vec.Word2Vec
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import java.io.File
import tm.util.Reader

object EvaluateTopics {
  
  def main(args: Array[String]): Unit = {
    //val topicFile = "nips1000.nodes.json"
    //val dataFile = "nips-1000-train-nolabel-bin.arff"
    val topicFile = "test.nodes.json"
    //val topicFile = "nodes.json"
    val dataFile = "papers9.Z66.sparse.txt"
    val topicTree = TopicTree.readJson(topicFile)
    //val topicTree = Extraction.readJson(topicFile).findSubTrees(_.name.equals("Z66"))
    val data = Reader.readData(dataFile)
    val coherences = topicTree.map { x =>  
      topicCoherence(x.words.map(_.toString()), data)
    }.toList()
    println(coherences.sum/coherences.size)
  }
  
  def topicCoherence(words: Seq[String], data: Data, m: Int = 4): Double = {
    val combination = words.take(m).toSet.subsets(2).map(_.toList)
    val logs = combination.map { x => 
      val word_i = x(0)
      val word_j = x(1)
      val coexists = data.df(Seq(word_i, word_j))
      val exists = data.df(word_j)
      //println(coexists+" "+exists+" "+(coexists+1)/exists)
      Math.log((coexists+1)/exists)
    }.toList
    logs.sum/Math.log(2)
  }
  
  def topicCompactness(words: Seq[String], data: Data, modelFile: String): Double = {
     val w2v = WordVectorSerializer.readWord2VecModel(modelFile)
     topicCompactness(words, data, w2v)
  }
  
  def topicCompactness(words: Seq[String], data: Data, w2v: Word2Vec): Double = {
    //val gModel = new File("/Developer/Vector Models/GoogleNews-vectors-negative300.bin.gz")
    val combination = words.toSet.subsets(2).map(_.toList)
    val similarities = combination.map { x => 
      val word_i = x(0)
      val word_j = x(1)
      val word_iVector = w2v.getWordVector(word_i)
      val word_jVector = w2v.getWordVector(word_j)
      val dotProduct = (0 until word_iVector.size).map{k => word_iVector(k)*word_jVector(k)}.sum
      val word_iNorm = (0 until word_iVector.size).map{k => word_iVector(k)*word_iVector(k)}.sum
      val word_jNorm = (0 until word_jVector.size).map{k => word_jVector(k)*word_jVector(k)}.sum
      dotProduct.toFloat / (word_iNorm*word_jNorm)
    }.toList
    similarities.sum/similarities.size
  }
}