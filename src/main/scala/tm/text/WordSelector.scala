package tm.text

object WordSelector {
  def basic(minCharacters: Int, minTf: Int, minDf: (Int) => Int): WordSelector = {
    new WordSelector {
      def select(ws: IndexedSeq[WordInfo], docCount: Int, maxWords: Int) = (
        ws.filter(w =>
          w.token.words.forall(_.length >= minCharacters)
            && w.tf >= minTf
            && w.df >= minDf(docCount)),
        Set.empty)
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
   * collection of words will have at most {@code maxWords} number of
   * words.
   */
  def byTfIdf(minCharacters: Int = 3, minDfFraction: Double = 0,
    maxDfFraction: Double = 0.25): WordSelector =
    new WordSelector {
      def select(ws: IndexedSeq[WordInfo], docCount: Int, maxWords: Int) = {
        val (eligibleWords, frequentWords) = {
          // filter words by constraints
          val filteredWords = ws.filter(w =>
            w.token.words.forall(_.length >= minCharacters)
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

        (selected, frequentWords.filter(_.tfidf >= minTfIdf).map(_.token).toSet)
      }

      val description = s"Select tokens by TF-IDF. Min characters: ${minCharacters}, minDfFraction: ${minDfFraction}, maxDfFraction: ${maxDfFraction}."
    }
  
  def byBurstiness() = {
    
  }
  
  def mixed() = {
    
  }
  
}

sealed trait WordSelector {
  /**
   * Given a dictionary and number of documents, perform word selections and
   * return another dictionary.  It also returns a set of tokens that should
   * be selected but are filtered away due to high document frequency.
   */
  def select(ws: IndexedSeq[WordInfo], docCount: Int,
    maxWords: Int): (IndexedSeq[WordInfo], Set[NGram])

  def description: String
}