package tm.text

import tm.test.BaseSpec
import java.util.regex.Pattern
import java.text.Normalizer
import tm.hillary.Emails

class DictionarySpec extends BaseSpec {
    implicit val stopwords = StopWords.read("stopwords.csv")

    trait HillaryDictionary extends Emails {
        Given("The first 500 emails")
        val countsByEmails = countWordsInEmails(500)

        When("The data is converted to bow")
        val dictionary =
            Preprocessor.buildDictionary(countsByEmails).filter(_.tf > 5)
    }

    describe("Dictionary built from Hillary emails") {
        it("should not contain any words with non-alphanumeric character") {
            val pattern = Pattern.compile(".*\\P{Alnum}+.*")
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
}