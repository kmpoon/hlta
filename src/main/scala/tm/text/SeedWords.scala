package tm.text

/**
 * Represents a file containing tokens to be selected.
 */
class SeedTokens(val tokens: Seq[NGram]) {
  private lazy val set = tokens.toSet

  lazy val max =
    if (tokens.length > 0) tokens.map(_.words.size).max else 0

  def contains(ngram: NGram) = set.contains(ngram)
  
  def involves(ngram: NGram) = set.exists(_.involves(ngram))
  
  def sort() = tokens.sortBy { token => -token.words.size }
}

object SeedTokens {
  //def read(file: String)(enc: String): SeedTokens = new SeedTokens(NGram.readFile(file)(enc))
  def read(file: String)(implicit enc: String = "UTF-8"): SeedTokens = {
    if(file.endsWith(".csv") && file.contains(".dict"))
      new SeedTokens(Dictionary.read(file)(enc).info.map(_.token))
    else
      new SeedTokens(NGram.readFile(file)(enc))
  }
  
  def Empty() = new SeedTokens(Seq.empty[NGram])
}