package pdf

import org.apache.pdfbox.pdmodel.PDDocument
import java.io.PrintWriter
import org.apache.pdfbox.util.PDFTextStripper
import java.io.File
import java.io.StringWriter

object ExtractText extends App {
    run

    def run() {
        if (args.length < 1) {
            printUsage
            return
        }

        if (new File(args(0)).isDirectory()) {
            if (args.length < 2) {
                printUsage
                return
            }

            new File(args(1)).mkdirs()

            extractDirectory(args(0), args(1))
        } else {
            extractFile(args(0))
        }

    }

    def printUsage() = {
        println("ExtractText input_file")
        println("ExtractText input_dir output_dir")
    }

    def getPath(components: String*) = components.mkString(File.separator)

    def extractDirectory(inputDir: String, outputDir: String) = {
        val directory = new File(inputDir)
        val files = directory.list().filter(_.endsWith(".pdf"))
        files.par.foreach { f =>
            val inputFile = getPath(inputDir, f)
            val outputFile = getPath(outputDir, getOutputFile(f))
            extractFile(inputFile, outputFile)
        }
    }

    def getOutputFile(inputFile: String) = inputFile.replaceAll(".pdf$", ".txt")

    def extractFile(inputFile: String): Unit =
        extractFile(inputFile, getOutputFile(inputFile))

    def extractFile(inputFile: String, outputFile: String): Unit = {
        val force = false;
        val sort = false;
        val separateBeads = true;
        val encoding = "UTF-8"
        val startPage = 1;
        val endPage = Integer.MAX_VALUE;

        val document = PDDocument.load(inputFile, false)
        val writer = new StringWriter()
        val output = new PrintWriter(outputFile, encoding)

        val stripper = new PDFTextStripper(encoding);
        stripper.setForceParsing(force);
        stripper.setSortByPosition(sort);
        stripper.setShouldSeparateByBeads(separateBeads);
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        stripper.writeText(document, writer);

        output.write(undoHyphenation(writer.toString))

        output.close
        document.close
    }

    def undoHyphenation(text: String) =
        text.replaceAll("""-\n(\S+)(\s*)""", "$1\n")
}