package tm.ijcai

import tm.text.Dictionary
import tm.text.NGram

object FindInterestingBigrams {
  def main(args: Array[String]) {
    val dictionary = Dictionary.read(args(0))
    // find n-grams where each of the token in that n-gram is also a token in
    // the dictionary
    val ngrams = dictionary.info.filter(w =>
      w.token.words.size > 1 &&
        w.token.words.map(NGram.apply).forall(dictionary.map.contains))
    ngrams.foreach { println }
  }
}