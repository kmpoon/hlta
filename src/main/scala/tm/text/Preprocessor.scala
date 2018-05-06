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
import scalaz.Scalaz._
import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import tm.util.ParMapReduce

/**
 * Preprocessor meant to be an API for HLTA users
 * See https://nlp.stanford.edu/software/tmt/tmt-0.4/ for reference
 */
object Preprocessor {
  type TokenCounts = Map[NGram, Int]
  
  /**
   * A simple English pre-built preprocessor
   * 
   * originally called preprocessor(minChars: Int)(s: Sentence)
   */
  def EnglishPreprocessor(s: String, minChars: Int = 4, stopwords: StopWords = StopWords.Empty()): Seq[String] = 
    tokenizeBySpace(s)
    .map(_.toLowerCase)
    .map(normalize)
    .map(StanfordLemmatizer.bracketRegex.replaceAllIn(_, ""))
    .map(replaceNonAlnum)
    .map(replaceStartingDigit)
    .filter(withProperLength(minChars))
    .filterNot(stopwords.contains)
    
  /**
   * A simple Korean pre-built preprocessor
   * 
   * It is called Korean because we need to tokenize sentence with space in advance
   * This works for non-ascii language like Chinese
   */
  def KoreanPreprocessor(s: String, stopwords: StopWords = StopWords.Empty()): Seq[String] = 
    tokenizeBySpace(s)
    .map(StanfordLemmatizer.bracketRegex.replaceAllIn(_, ""))
    .map(replacePunctuation)
    .map(replaceStartingDigit)
    .filter(withLength(1))
    .filterNot(stopwords.contains)
  
  /**
   * Performs normalization (removing accents), removes punctuation, words
   * shorter than 4 characters, and change letters to lower case.
   * Shall be removed in the future
   */
  @Deprecated
  def preprocess(text: String, minChars: Int = 4) = {

    def convert(original: String) = {
      val conversions = {
        if(minChars>2)
          ("'", "") +:
            ("[^\\p{Alpha}\\n]+" -> " ") +:
            (s"\\b\\w{1,${minChars - 1}}\\b" -> " ") +: // remove words with fewer than 4 characters
            ("^\\s+" -> "") +:
            ("\\s+$" -> "") +:
            Nil
        else
          ("'", "") +:
            ("[^\\p{Alpha}\\n]+" -> " ") +:
            ("^\\s+" -> "") +:
            ("\\s+$" -> "") +:
            Nil
      }

      conversions.foldLeft(original) {
        (text, rule) => text.replaceAll(rule._1, rule._2)
      }
    }

    convert(normalize(text.toLowerCase))
  }
  

  //Tokenization methods
  
  def tokenizeBySpace(text: String): Seq[String] = tokenizeByRegex(text, "\\s+")
  
  def tokenizeByRegex(text: String, regex: String): Seq[String] = 
    //It needs to handle the case of an empty string, otherwise an array
    //containing a single element of empty string will be returned.
    if (text.isEmpty) Array.empty[String]
    else text.split(regex)

    
  //Replacement  
  
  val NonAlnum = "\\P{Alnum}".r
  val Digit = "^(\\p{Digit})".r
  val Punctuation = "\\p{Punct}".r

  def useRegexToReplace(pair: (Regex, (Match) => String)) = pair match {
    case (r, m) => (input: String) => r.replaceAllIn(input, m)
  }
  
  def replaceNonAlnum(input: String): String = NonAlnum.replaceAllIn(input, (m: Match) => "")
  
  def replaceStartingDigit(input: String): String = Digit.replaceAllIn(input, (m: Match) => s"${m.group(1)}")
  
  def replacePunctuation(input: String): String = Punctuation.replaceAllIn(input, (m: Match) => "")
  
  /** 
   *  Perform compatibility decomposition, followed by canonical composition, 
   *  to convert ligature (fi) and remove accents
   */
  def normalize(text: String) =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
      
  
  //Condition
  
  def withProperLength(min: Int)(word: String) = 
    word.replaceAll("[^\\p{Alpha}\\n]+", "").length >= min

  def withLength(min: Int)(word: String) = 
    word.length >= min
  
  @Deprecated
  def tokenizeAndRemoveStopWords(text: String)(
    implicit stopWords: StopWords): Seq[String] =
    tokenizeBySpace(text).filterNot(_.isEmpty).filterNot(stopWords.contains)

  /**
   * Builds n-gram from a slide window of length n.
   */
  def buildNGrams(words: Seq[String], n: Int): Seq[NGram] =
    if (n <= 1) words.map(NGram(_))
    else words.sliding(n).map(NGram(_)).toSeq

  /**
   * Returns a collection of tokens lists consisting of n-grams, where n is 1 to
   * {@code maxN}.
   */
  def find1ToNGrams(words: Seq[String], maxN: Int = 1): IndexedSeq[Seq[NGram]] =
    (1 to maxN).map(buildNGrams(words, _))

  def tokenizeAndCount(text: String, n: Int = 1)(implicit stopWords: StopWords) =
    DataConverter.countTokens(
      find1ToNGrams(tokenizeBySpace(text).filterNot(_.isEmpty).filterNot(stopWords.contains), n).flatten)

  //    def add(p1: TokenCounts, p2: TokenCounts): TokenCounts = {
  //        type mutableMap = mutable.Map[NGram, Int]
  //        def add(m: mutableMap, p: (NGram, Int)): mutableMap = {
  //            val (key, value) = p
  //            m.get(key) match {
  //                case Some(v) => m += (key -> (v + value))
  //                case None => m += (key -> value)
  //            }
  //            m
  //        }
  //
  //        val map: mutableMap = mutable.Map.empty ++= p1
  //        p2.foldLeft(map)(add).toMap
  //    }

      
  def sumWordCounts(countsByDocuments: GenSeq[TokenCounts]) =
    countsByDocuments.reduce(_ |+| _)

  def computeDocumentFrequencies(countsByDocuments: GenSeq[TokenCounts]) = {
    def toBinary(c: Int) = if (c > 0) 1 else 0

    // convert to binary and then add up
    countsByDocuments
      .map(_.map { wc => (wc._1, toBinary(wc._2)) })
      .reduce(_ |+| _)
  }

  def computeTfIdf(tf: Int, df: Int, numberOfDocuments: Int): Double =
    tf * Math.log(numberOfDocuments.toDouble / df)

  def buildDictionary(countsByDocuments: GenSeq[TokenCounts]) = {
    val termFrequencies = sumWordCounts(countsByDocuments)
    val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
    val N = countsByDocuments.size

    def buildWordInfo(token: NGram, tf: Int, df: Int) =
      WordInfo(token, tf, df, computeTfIdf(tf, df, N))

    val info = termFrequencies.keys.map { w =>
      buildWordInfo(w, termFrequencies(w), documentFrequencies(w))
    }

    Dictionary.buildFrom(info)
  }

  /*
     * Computes the TF-IDF of words.  The TF-IDF is given by:
     *
     * tf-idf(t, D) = tf(t, D) * log (N / n_t)
     *
     * @param filter determines whether a word should be included based on the
     * term frequency and document frequency.
     */
  def computeTfIdf(countsByDocuments: GenSeq[TokenCounts]): Map[NGram, Double] = {
    val counts = sumWordCounts(countsByDocuments)
    val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
    val N = countsByDocuments.size

    computeTfIdf(N, counts, documentFrequencies)
  }

  def computeTfIdf(numberOfDocuments: Int, termFrequencies: TokenCounts,
    documentFrequencies: TokenCounts): Map[NGram, Double] = {
    val N = numberOfDocuments
    termFrequencies.keys.map { word =>
      (word -> computeTfIdf(
        termFrequencies(word), documentFrequencies(word), N))
    }.toMap
  }

  //    /**
  //     * Sort with tf-idf in descending order and then by word in ascending order
  //     */
  //    def order(tfidf1: (String, Double), tfidf2: (String, Double)) = {
  //        val cmp1 = -tfidf1._2.compare(tfidf2._2)
  //        val cmp2 =
  //            if (cmp1 == 0)
  //                tfidf1._1.compareTo(tfidf2._1)
  //            else
  //                cmp1
  //        cmp2 < 0
  //    }
  //
  //    def buildWordIndices(tfidf: Map[String, Double]) = {
  //        val words = tfidf.toVector.sortWith(order).map(_._1)
  //        val map = words.zipWithIndex.toMap
  //        (words, map)
  //    }

  /**
     * Replace the constituent tokens by n-grams, up to a specified
     * {@code maxN}.
     *
     * For example, if "hong" and "kong" are two consecutive
     * tokens, and check "hong-kong" returns true, then "hong-kong" will be
     * included in the resulting sequence but not two individual words "hong"
     * and "kong".
     *
     * @param words sequence of words.
     * @param check used to check whether a n-gram will be used.
     */
  def replaceConstituentTokensByNGrams(
    tokens: Seq[NGram], check: (NGram) => Boolean): Seq[NGram] = {
    replaceByNGrams(tokens, check, 2)
  }

  def replaceConstituentTokensByNGrams(
    sentence: Sentence, check: (NGram) => Boolean): Sentence = {
    new Sentence(replaceConstituentTokensByNGrams(sentence.tokens, check))
  }
  
  def replaceByNGrams(sentence: Sentence, check: (NGram) => Boolean, n: Int): Sentence = {
    new Sentence(replaceByNGrams(sentence.tokens, check, n))
  }

  /**
   * Replaces the constituent words in the token sequence by the n-grams.
   */
  def replaceByNGrams(tokens: Seq[NGram],
    check: (NGram) => Boolean, n: Int): Seq[NGram] = {

    case class State(result: Seq[NGram],
      remaining: Seq[NGram], buffer: Queue[NGram])

    /**
     * Adds the next token from the remaining list, if there is, to the
     * buffer.
     */
    def addTokenToBuffer(state: State): State = {
      import state._
      if (remaining.isEmpty) state
      else State(result, remaining.tail, buffer :+ remaining.head)
    }

    /**
     * Moves a word from the buffer to the result sequence.
     */
    def moveFromBufferToResult(state: State): State = {
      import state._
      val (head, queue) = buffer.dequeue
      State(head +: result, remaining, queue)
    }

    @tailrec
    def process(state: State): State = {
      import state._

      if (buffer.size == n) {
        // check whether the next combined tokens should be included
        val next = NGram(buffer.flatMap(_.words))
        if (check(next))
          process(State(next +: result, remaining, Queue.empty))
        else {
          process(moveFromBufferToResult(state))
        }
      } else if (!remaining.isEmpty)
        process(addTokenToBuffer(state))
      else if (buffer.size > 0) // remaining is empty
        process(moveFromBufferToResult(state))
      else
        state
    }

    process(State(Nil, tokens, Queue.empty)).result.reverse
  }
  
  def main(args: Array[String]){
    //val s = "testing string and some more testing string"
    import scala.io.Source
    val s = Source.fromFile("./docSample/3D Human Pose Estimation = 2D Pose Estimation + Matching.txt").getLines().mkString(" ")
    val ss = EnglishPreprocessor(s)
    
    println(ss.mkString(","))
  }
}