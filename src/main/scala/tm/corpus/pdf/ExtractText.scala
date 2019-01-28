package tm.corpus.pdf

import java.io.StringWriter
import java.nio.file.Path
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import tm.corpus.Extractor
import org.slf4j.LoggerFactory


object ExtractText extends Extractor {
  
  val extension = "pdf"

  def main(args: Array[String]) {
    run(args)
  }

  override def extractSingleText(inputFile: Path): String = {
    val force = false;
    val sort = false;
    val separateBeads = true;
    //     val encoding = "UTF-8"
    val startPage = 1;
    val endPage = Integer.MAX_VALUE;

    try {
      
    val document = PDDocument.load(inputFile.toFile)
    val writer = new StringWriter()

    try {
      val stripper = new PDFTextStripper();
      //stripper.setForceParsing(force);
      stripper.setSortByPosition(sort);
      stripper.setShouldSeparateByBeads(separateBeads);
      stripper.setStartPage(startPage);
      stripper.setEndPage(endPage);
      stripper.writeText(document, writer);

      undoHyphenation(writer.toString)
    } catch {
      case e: Exception => 
        logger.error("Unable to extract "+inputFile.toString+", treating it as empty document instead")
        if(logger.isDebugEnabled)
          e.printStackTrace()
        ""
    } finally {
      document.close()
      writer.close()
    }
    
    } catch {
      case e: Exception => 
        logger.error("Invalid PDF "+inputFile.toString+", treating it as empty document instead")
        if(logger.isDebugEnabled)
          e.printStackTrace()
        ""
    }
  }
}