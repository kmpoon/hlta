package tm.text

/**
 * Represents a file containing tokens to be selected.
 */
class SeedTokens(val tokens: Seq[NGram]) {
  private lazy val set = tokens.toSet

  lazy val max =
    if (tokens.length > 0) tokens.map(_.words.size).max else 0

  def contains(ngram: NGram) = set.contains(ngram)
}

object SeedTokens {
  def read(file: String): SeedTokens = new SeedTokens(NGram.readFile(file))
}