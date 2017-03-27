package tm.text

object WordSelector {
  def basic(minCharacters: Int, minTf: Int, minDf: (Int) => Int): WordSelector = {
    new WordSelector {
      def select(d: Dictionary, df: Int) =
        (d.filter(w =>
          w.token.words.forall(_.length > minCharacters)
            && w.tf >= minTf
            && w.df >= minDf(df)),
          Set.empty)
    }
  }

  def byTfIdf(minCharacters: Int, minDfFraction: Double,
    maxDfFraction: Double, maxWords: Int): WordSelector =
    new WordSelector {
      def select(d: Dictionary, df: Int) = {
        val filteredWords = d.info.filter(w =>
          w.token.words.forall(_.length > minCharacters)
            && w.df >= minDfFraction * df)

        val eligibleWords =
          filteredWords.filter(_.df <= maxDfFraction * df)
        val frequentWords =
          filteredWords.filterNot(_.df <= maxDfFraction * df)
        val filteredAndSortedWords =
          eligibleWords.sortBy(w => (-w.tfidf, w.token.identifier))

        val (selected, frequentWords1) = if (filteredAndSortedWords.size > maxWords) {
          // using the tfidf of the (maxWords+1)-th word as minimum 
          val minTfIdf = filteredAndSortedWords(maxWords).tfidf
          val s = filteredAndSortedWords.takeWhile(_.tfidf > minTfIdf)
          val fws = frequentWords.filter(_.tfidf > minTfIdf)
          (s, fws)
        } else {
          val minTfIdf = filteredAndSortedWords.last.tfidf
          val fws = frequentWords.filter(_.tfidf > minTfIdf)
          (filteredAndSortedWords, fws)
        }

        (Dictionary.buildFrom(selected), frequentWords1.map(_.token).toSet)
      }
    }
}

sealed trait WordSelector {
  /**
   * Given a dictionary and number of documents, perform word selections and
   * return another dictionary.  It also returns a set of tokens that should
   * be selected but are filtered away due to high document frequency.
   */
  def select(d: Dictionary, n: Int): (Dictionary, Set[NGram])
}