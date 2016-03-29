package tm.hillary

import tm.text.StopWords
import tm.text.StanfordLemmatizer
import tm.text.Sentence
import tm.text.Preprocessor
import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import java.io.FileInputStream
import java.io.PrintWriter
import java.nio.file.Paths
import tm.text.DataConverter.Settings

object ExtractText {
    val minChars = 3

    def main(args: Array[String]) {
        if (args.length < 2)
            printUsage
        else
            run(args(0), args(1))
    }

    def printUsage() = {
        println("ExtractText input_file output_dir")
    }

    def run(inputFile: String, outputDir: String) = {
        implicit val stopWords = StopWords.implicits.default
        implicit val settings = Parameters.implicits.settings

        val output = Paths.get(outputDir)
        if (!output.toFile.exists)
            output.toFile.mkdirs()

        val input = new FileInputStream(inputFile)
        Emails.readEmails(input).par
            .foreach { e =>
                val target = output.resolve(f"email${e.id}%04d.txt")
                val writer = new PrintWriter(target.toString)
                writer.println(documentToString(preprocess(e)))
                writer.close
            }
        input.close
    }

    def preprocess(email: Email)(
            implicit stopwords: StopWords, settings: Settings) = {
        val subjectSentence = StanfordLemmatizer.processAsSentence(email.subject)
        val sentences = subjectSentence +:
            StanfordLemmatizer.process(email.body).sentences

        sentences.map(Preprocessor.preprocess(minChars))
    }

    def documentToString(tokens: Seq[Seq[String]]) =
        tokens.map(_.mkString(" ")).mkString("\n")

}