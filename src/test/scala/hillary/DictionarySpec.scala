package hillary

import hillary.test.BaseSpec
import java.util.regex.Pattern
import java.text.Normalizer

class DictionarySpec extends BaseSpec {

    trait HillaryDictionary {
        val dictionary = Dictionary.read("hillary-dict.csv")
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