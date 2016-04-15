package tm.text

object WordSelector {
  /**
   * Given a dictionary and number of documents, perform word selections and
   * return another dictionary.
   */
  type Type = (Dictionary, Int) => Dictionary

  def basic(minCharacters: Int, minTf: Int, minDf: (Int) => Int) = {
    (d: Dictionary, df: Int) =>
      d.filter(w =>
        w.token.words.forall(_.length > minCharacters)
          && w.tf >= minTf
          && w.df >= minDf(df))
  }

  def byTfIdf(minCharacters: Int, minDfFraction: Double,
    maxDfFraction: Double, maxWords: Int) = {
    (d: Dictionary, df: Int) =>
      {
        val filteredAndSortedWords =
          d.info.filter(w =>
            w.token.words.forall(_.length > minCharacters)
              && w.df <= maxDfFraction * df
              && w.df >= minDfFraction * df)
            .sortBy(w => (-w.tfidf, w.token.identifier))
        val selected = if (filteredAndSortedWords.size > maxWords) {
          val minTfIdf = filteredAndSortedWords(maxWords + 1).tfidf
          filteredAndSortedWords.takeWhile(_.tfidf > minTfIdf)
        } else
          filteredAndSortedWords

        Dictionary.buildFrom(selected)
      }
  }
}