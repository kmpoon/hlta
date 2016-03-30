package tm.text

import tm.test.BaseSpec

class NGramSpec extends BaseSpec {
  describe("Equality on two 2-grams") {
    it("should return true for the same 2-grams constructed from different ways") {
      NGram.fromConcatenatedString("syria-aiding") ==
        NGram.fromWords("syria", "aiding") should be(true)
    }
  }
}