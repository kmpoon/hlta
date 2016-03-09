package hillary

import java.io.PrintWriter
import scala.io.Source

/**
 * Info about a word.
 *
 * tf: term frequency (i.e. number of occurrences in all document.
 * df: document frequency (i.e. number of documents with
 */
case class WordInfo(word: String, tf: Int, df: Int, tfidf: Double)

object Dictionary {
    def order(w1: WordInfo, w2: WordInfo) = {
        val cmp1 = -w1.tfidf.compare(w2.tfidf)
        val cmp2 =
            if (cmp1 == 0)
                w1.word.compareTo(w2.word)
            else
                cmp1

        cmp2 < 0
    }

    def buildFrom(w: Iterable[WordInfo]) = {
        val info = w.toVector.sortWith(order)
        val map = info.zipWithIndex.map(p => (p._1.word -> p._2)).toMap
        new Dictionary(info, map)
    }

    def read(filename: String) = {
        buildFrom(
            Source.fromFile(filename).getLines
                .drop(1) // skip the header
                .map(_.split(","))
                .map(_ match {
                    case Array(w, tf, df, tfidf) =>
                        WordInfo(w, tf.toInt, df.toInt, tfidf.toDouble)
                })
                .toIterable)
    }
}

class Dictionary(val info: IndexedSeq[WordInfo], val map: Map[String, Int]) {

    def getInfo(word: String) = info(map(word))

    def filter(pred: (WordInfo) => Boolean) =
        Dictionary.buildFrom(info.filter(pred))

    def words() = info.map(_.word)

    def getMap[T](f: (WordInfo) => T) = map.mapValues(i => f(info(i)))

    def save(filename: String) = {
        val writer = new PrintWriter(filename)

        writer.println("word,tf,df,tfidf")
        info.map(i => s"${i.word},${i.tf},${i.df},${i.tfidf}")
            .foreach(writer.println)

        writer.close
    }
}
