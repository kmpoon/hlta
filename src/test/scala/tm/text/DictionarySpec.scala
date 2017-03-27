package tm.text

import tm.test.BaseSpec
import java.util.regex.Pattern
import java.text.Normalizer
import tm.hillary.TestEmails

class DictionarySpec extends BaseSpec {
  implicit val stopwords = StopWords.read("stopwords.csv")

  trait HillaryDictionary extends TestEmails {
    Given("The first 500 emails")
    val countsByEmails = countWordsInEmails(500)

    When("The dictionary is built")
    val dictionary =
      Preprocessor.buildDictionary(countsByEmails).filter(_.tf > 5)
  }

  describe("Dictionary built from Hillary emails") {
    it("should contain words with only alphanumeric characters or underscores") {
      val pattern = Pattern.compile(".*[^\\p{Alnum}_]+.*")
      new HillaryDictionary {
        dictionary.words.filter(
          w => pattern.matcher(w).matches()) shouldBe empty
      }
    }

    it("should not contain any words with accents") {
      def isNormalized(word: String) =
        Normalizer.isNormalized(word, Normalizer.Form.NFD)

      new HillaryDictionary {
        dictionary.words.filterNot(isNormalized) shouldBe empty
      }

    }
  }

  describe("Words with accents and symbols") {
    val words = List("lendingtree®", "mccaul■", "déjà", "mobile®", "naïve",
      "faifl§uardian", "lowey■", "®", "copying‘lona", "lady–like",
      "«august", "thoughts„", "brownback■", "‘termination", "détente",
      "iãiituardian", "“")

    they("should be cleaned properly") {
    }
  }

  trait PapersDictionary {
    Given("Dictionary")
    val dictionary = Dictionary.read(
      getClass.getResourceAsStream("/papers2.dict-10000.csv"))
    //      val dictionary = Dictionary.read(
    //    new GZIPInputStream(getClass.getResourceAsStream("/Emails.csv.gz")))
  }

  describe("Dictionary built from words extracted from AI papers") {
    new PapersDictionary {
      it("should contain 10000 words") {
        dictionary.info should have size 10000
      }

      it("should contain the word bayesian after selection") {
        val settings =
          DataConverter.Settings(concatenations = 2, minCharacters = 3,
            wordSelector = WordSelector.byTfIdf(3, 0, .25, 100))

        val (selected, frequent) = settings.wordSelector.select(dictionary, 20000)
        selected.map.keys should not contain("bayesian")
        frequent should contain("bayesian")
      }
    }
  }
}