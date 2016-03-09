package tm.text

import org.apache.commons.csv.CSVFormat
import java.text.SimpleDateFormat
import java.io.FileReader
import java.util.Date
import java.text.Normalizer
import scala.annotation.tailrec
import scala.collection.GenSeq
import scala.collection.JavaConversions._
import scala.collection.immutable.Queue
import scala.collection.immutable.Queue
import scala.collection.mutable

object Preprocessor {
    type WordCounts = Map[String, Int]

    val stopWords = StopWords.read("stopwords.csv")

    def preprocess(subject: String, body: String): String = {
        val text = (subject + "\n" + body).toLowerCase

        // to remove accents
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")

        val conversions = Seq(
            "'" -> "",
            "\\P{Alpha}+" -> " ",
            "\\b\\w{1,3}\\b" -> " ", // remove words with fewer than 4 characters
            "^\\s+" -> "",
            "\\s+$" -> "")

        conversions.foldLeft(normalized) {
            (text, rule) => text.replaceAll(rule._1, rule._2)
        }
    }

    def filter(counts: WordCounts): WordCounts = {
        counts
            .filter(p => !stopWords.contains(p._1))
            .filter(_._2 >= 5)
    }

    def tokenizeBySpace(text: String): Seq[String] =
        // It needs to handle the case of an empty string, otherwise an array
        // containing a single element of empty string will be returned.
        if (text.isEmpty) Array.empty[String]
        else text.split("\\s+").filter(!stopWords.contains(_))

    /**
     * Returns a list of n-grams built from the sequence of words.
     */
    def buildNGrams(words: Seq[String], n: Int): Seq[String] =
        if (n <= 1) words
        else words.sliding(n).map(_.mkString("-")).toSeq

    /**
     * Returns a collection of tokens lists consisting of n-grams, where n is 1 to
     * {@code maxN}.
     */
    def find1ToNGrams(words: Seq[String], maxN: Int = 1): IndexedSeq[Seq[String]] =
        (1 to maxN).map(buildNGrams(words, _))

    /**
     * Counts number of words in the given sequence of words.
     */
    def countWords(words: Seq[String]): WordCounts = {
        if (words.isEmpty) {
            Map.empty
        } else {
            words.par.map(w => Map(w -> 1)).reduce(add)
        }
    }

    def tokenizeAndCount(text: String, n: Int = 1) =
        countWords(find1ToNGrams(tokenizeBySpace(text), n).flatten)

    def add(p1: WordCounts, p2: WordCounts): WordCounts = {
        def add(m: mutable.Map[String, Int], p: (String, Int)): mutable.Map[String, Int] = {
            val (key, value) = p
            m.get(key) match {
                case Some(v) => m += (key -> (v + value))
                case None => m += (key -> value)
            }
            m
        }

        val map: mutable.Map[String, Int] = mutable.Map.empty ++= p1
        p2.foldLeft(map)(add).toMap
    }

    def sumWordCounts(countsByDocuments: GenSeq[WordCounts]) =
        countsByDocuments.reduce(add)

    def computeDocumentFrequencies(countsByDocuments: GenSeq[WordCounts]) = {
        def toBinary(c: Int) = if (c > 0) 1 else 0

        // convert to binary and then add up
        countsByDocuments
            .map(_.map { wc => (wc._1, toBinary(wc._2)) })
            .reduce(add)
    }

    def computeTfIdf(tf: Int, df: Int, numberOfDocuments: Int): Double =
        tf * Math.log(numberOfDocuments.toDouble / df)

    def buildDictionary(countsByDocuments: GenSeq[WordCounts]) = {
        val termFrequencies = sumWordCounts(countsByDocuments)
        val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
        val N = countsByDocuments.size

        def buildWordInfo(word: String, tf: Int, df: Int) =
            WordInfo(word, tf, df, computeTfIdf(tf, df, N))

        val info = termFrequencies.keys.map { w =>
            buildWordInfo(w, termFrequencies(w), documentFrequencies(w))
        }

        Dictionary.buildFrom(info)
    }

    /**
     * Computes the TF-IDF of words.  The TF-IDF is given by:
     *
     * tf-idf(t, D) = tf(t, D) * log (N / n_t)
     *
     * @param filter determines whether a word should be included based on the
     * term frequency and document frequency.
     */
    def computeTfIdf(countsByDocuments: GenSeq[WordCounts]): Map[String, Double] = {
        val counts = sumWordCounts(countsByDocuments)
        val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
        val N = countsByDocuments.size

        computeTfIdf(N, counts, documentFrequencies)
    }

    def computeTfIdf(numberOfDocuments: Int, termFrequencies: WordCounts,
        documentFrequencies: WordCounts): Map[String, Double] = {
        val N = numberOfDocuments
        termFrequencies.keys.map { word =>
            (word -> computeTfIdf(
                termFrequencies(word), documentFrequencies(word), N))
        }.toMap
    }

    /**
     * Sort with tf-idf in descending order and then by word in ascending order
     */
    def order(tfidf1: (String, Double), tfidf2: (String, Double)) = {
        val cmp1 = -tfidf1._2.compare(tfidf2._2)
        val cmp2 =
            if (cmp1 == 0)
                tfidf1._1.compareTo(tfidf2._1)
            else
                cmp1
        cmp2 < 0
    }

    def buildWordIndices(tfidf: Map[String, Double]) = {
        val words = tfidf.toVector.sortWith(order).map(_._1)
        val map = words.zipWithIndex.toMap
        (words, map)
    }

    type NGram = Seq[String]

    /**
     * Tokenizes the text without including the constituent tokens.
     * For example, if "hong" and "kong" are two consecutive words, and
     * check "hong-kong" returns true, then "hong-kong" will be included in the
     * resulting sequence but not two individual words "hong" and "kong".
     *
     * @param words sequence of words.
     * @param check used to check whether a n-gram will be used.
     * @param maxN the maximum n for which n-gram will be used.
     */
    def tokenizeWithoutConstituentTokens(words: Seq[String],
        check: (String) => Boolean, maxN: Int): Seq[String] = {

        @tailrec
        def rec(tokens: Seq[NGram], n: Int): Seq[NGram] =
            if (n <= 1) tokens
            else rec(replaceByNGrams(tokens, check, n), n - 1)

        rec(words.map(Seq(_)), maxN).map(_.mkString("-"))
    }

    /**
     * Replaces the constituent words in the token sequence by the n-grams.
     */
    def replaceByNGrams(tokens: Seq[NGram],
        check: (String) => Boolean, n: Int): Seq[NGram] = {

        case class State(result: Seq[NGram],
            remaining: Seq[NGram], buffer: Queue[String])

        /**
         * Adds the next 1-gram from the remaining list to the buffer.  If the
         * first token in the remaining list is not a 1-gram, it is added to
         * the result sequence.  This is repeated until a 1-gram is added or
         * the remaining list has been exhausted.
         */
        @tailrec
        def add1GramToBuffer(state: State): State = {
            import state._
            if (remaining.isEmpty)
                state
            else if (remaining.head.size == 1)
                State(result, remaining.tail, buffer :+ remaining.head.head)
            else
                add1GramToBuffer(State(remaining.head +: result, remaining.tail, buffer))
        }

        /**
         * Moves a word as 1-gram from the buffer to the result sequence.
         */
        def moveFromBufferToResult(state: State): State = {
            import state._
            val (head, queue) = buffer.dequeue
            State(Seq(head) +: result, remaining, queue)
        }

        @tailrec
        def process(state: State): State = {
            import state._

            if (buffer.size == n) {
                if (check(buffer.mkString("-")))
                    process(State(buffer.toSeq +: result, remaining, Queue.empty))
                else {
                    process(moveFromBufferToResult(state))
                }
            } else if (!remaining.isEmpty)
                process(add1GramToBuffer(state))
            else if (buffer.size > 0)
                process(moveFromBufferToResult(state))
            else
                state
        }

        process(State(Nil, tokens, Queue.empty)).result.reverse
    }

}