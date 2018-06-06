package tm.text

import scala.collection.GenSeq
import tm.util.ParMapReduce.mapReduce

object WordSelector {
  def basic(minCharacters: Int = 3, minTf: Int = 6, minDf: (Int) => Int = (Int) => 6): WordSelector = {
    new WordSelector {
      def select(ws: IndexedSeq[TfidfWordInfo], docCount: Int, maxWords: Int) = (
        ws.filter(w =>
          w.token.words.forall(_.length >= minCharacters)
            && w.tf >= minTf
            && w.df >= minDf(docCount)),
        IndexedSeq.empty)
      val description = s"Select tokens by basic constraints. Min characters: ${minCharacters}, minTf: ${minTf}, minDf: ${minDf}."
    }
  }

  /**
   * Used to filter words based on TF-IDF.  The words must:
   *
   * - have at least {@code minCharacters} characters;
   * - appear in at least {@code minDfFraction} of documents; and
   * - appear in as most {@code maxDfFraction} of documents.
   *
   * If the eligible words have more than {@code maxWords} words, then
   * the words will be ordered according to TF-IDF.  Only those words
   * that have TF-IDF greater than the TF-IDF of the word at
   * the ({@code maxWords} + 1) position will be retained.  The resulting
   * collection of words will have exact {@code maxWords} number of
   * words.
   */
  def byTfIdf(minCharacters: Int = 3, minDfFraction: Double = 0,
    maxDfFraction: Double = 0.25): WordSelector =
    new WordSelector {
      def select(ws: IndexedSeq[TfidfWordInfo], docCount: Int, maxWords: Int) = {
        val (eligibleWords, frequentWords) = {
          // filter words by constraints
          val filteredWords = ws.filter(w =>
            w.token.words.forall(_.length >= minCharacters)
              && w.df > 1
              && w.df >= minDfFraction * docCount)

          val (eligibleWords, frequentWords) =
            filteredWords.partition(_.df <= maxDfFraction * docCount)

          val filteredAndSortedWords =
            eligibleWords.sortBy(w => (-w.tfidf, w.token.identifier))

          (filteredAndSortedWords, frequentWords)
        }

//        val (selected, minTfIdf) =
//          if (eligibleWords.size > maxWords) {
//            // using the tf-idf of the (maxWords+1)-th word as minimum
//            val minTfIdf = eligibleWords(maxWords).tfidf
//            val s = eligibleWords.takeWhile(_.tfidf > minTfIdf)
//            (s, minTfIdf)
//          } else {
//            val minTfIdf = eligibleWords.last.tfidf
//            (eligibleWords, minTfIdf)
//          }
          //Since eligible are already sorted by tfidf
          val selected = eligibleWords.take(maxWords)
          val minTfIdf = if(selected.isEmpty) 0 else selected.last.tfidf

        (selected, frequentWords.filter(_.tfidf >= minTfIdf))
      }

      val description = s"Select tokens by TF-IDF. Min characters: ${minCharacters}, minDfFraction: ${minDfFraction}, maxDfFraction: ${maxDfFraction}."
    }
  

//  
//  def byAssociatedWords(maxAssociatedMatches: Int, minDfFraction: Double = 0.01, maxDfFraction: Double = 0.2) = {
//    new WordSelector {
//      type TokenCounts = Map[NGram, Int]
//       
//      def select(ws: IndexedSeq[WordInfo], docCount: Int, maxWords: Int, preSelected: IndexedSeq[WordInfo]) = {
//        // filter words by constraints
//        val associatedWords = preSelected.flatMap(_.associatedWords.take(maxAssociatedMatches)).toSet.toIndexedSeq
//        val remaining = ws.filterNot{associatedWords.contains(_)}
//        (associatedWords, remaining)
//      }
//      
//      def description: String = s"Select tokens by ${maxAssociatedMatches} best associated words}"
//    }
//  }  

//  def byBurstiness(startTime: Int, endTime: Int, increment: Int = 1, minDfFraction: Double = 0.01, maxDfFraction: Double = 0.2) = {
//    import tm.util.LinearRegression
//    new WordSelector {
//      def select(ws: IndexedSeq[WordInfo], docCount: Int, maxWords: Int) = {
//        // filter words by constraints
//        val (filteredWords, failedWords) = ws.partition{w => w.df >= minDfFraction*docCount && w.df < maxDfFraction*docCount}
//        val wsWithBustiness = filteredWords.map{wordInfo =>
//          val values = (startTime until endTime by increment).map(wordInfo.trend.getOrElse(_, 0)).zipWithIndex.map{case(y, x)=>(x.toDouble, y.toDouble)}
//          (wordInfo, tm.util.LinearRegression(values)._1)
//        }
//        val sortedBurstyWords = wsWithBustiness.sortBy{case(wordInfo, slope) => -slope}.map(_._1)
//        val (burstyWords, remainingWords) = sortedBurstyWords.splitAt(maxWords)
//
//        (burstyWords, failedWords ++ remainingWords)
//      }
//      
//       def description: String = s"Select tokens by trend, during ${startTime} and ${endTime} with minimum df fraction ${minDfFraction}"
//    }
//  }
//  
//  def mixed(wordSelector1: WordSelector, wordSelector2: WordSelector, ratio: Double) = {
//    new WordSelector {
//      def select(ws: IndexedSeq[WordInfo], docCount: Int, maxWords: Int) = {
//        val (eligibleWords1, remainingWords) = wordSelector1.select(ws, docCount, (maxWords*ratio).toInt)
//        //println(eligibleWords1.size)
//        
//        val (eligibleWords2, frequentWords) = wordSelector2.select(remainingWords, docCount, maxWords-eligibleWords1.size)
//        //println(eligibleWords2.size)
//        (eligibleWords1++eligibleWords2, frequentWords)
//      }
//      
//      def description: String = "Mixed Word Selector: "+wordSelector1.description+" ; "+wordSelector2.description
//    }
//  }
  
}

sealed trait WordSelector {
  /**
   * Given a dictionary and number of documents, perform word selections and
   * return another dictionary.  It also returns a set of tokens that should
   * be selected but are filtered away due to high document frequency.
   */
  def select(ws: IndexedSeq[TfidfWordInfo], docCount: Int, maxWords: Int): (IndexedSeq[TfidfWordInfo], IndexedSeq[TfidfWordInfo])

  def description: String
}