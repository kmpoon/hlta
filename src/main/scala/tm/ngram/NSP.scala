package tm.ngram

import tm.util.Arguments
import org.slf4j.LoggerFactory
import scala.io.Source
import tm.text.NGram
import java.io.PrintWriter
import tm.text.SeedTokens
import tm.text.Dictionary
import tm.util.manage
import scala.annotation.tailrec
import tm.text.Convert
import java.nio.file.Paths
import java.nio.file.Path
import tm.text.Preprocessor

/**
 * Tools for Ngram Statistics Package (http://ngram.sourceforge.net).
 * 
 * TODO: It seems that sometimes not all seed tokens are used in during 
 * conversion.  For example, not all tokens are used for reuters21578 
 * data set. 
 */
object NSPSelect {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner(s"Usage: ${NSPSelect.getClass.getName.replaceAll("\\$$", "")} [OPTIONS]... statistics-file number [out-file]")
    val sourceDir = opt[String](descr = "check the n-grams with the given source directory")
    val statFile = trailArg[String](descr = "NSP statistics file")
    val number = trailArg[Int](descr = "Number of tokens to be selected")
    val outFile = trailArg[String](descr = "Output file")

    verify
    checkDefaultOpts()
  }

  val logger = LoggerFactory.getLogger(NSPSelect.getClass)

  def main(args: Array[String]) {
    val conf = new Conf(args)
    //    check(conf.outFile())
    run(conf.statFile(), conf.number(),
      conf.outFile.toOption, conf.sourceDir.toOption)
  }

  def run(file: String, number: Int,
    outFile: Option[String], sourceDir: Option[String]) = {

    val check: (NGram) => Boolean = sourceDir match {
      case Some(s) => checkWithFiles(Paths.get(s), _)
      case None    => (_: NGram) => true
    }

    @tailrec
    def loop(source: Stream[NGram], selected: Seq[NGram]): Seq[NGram] = {
      if (selected.size >= number || source.isEmpty)
        return selected.reverse

      val t = source.head
      if (check(t)) {
        loop(source.tail, t +: selected)
      } else {
        logger.info("The token {} is not selected since it does not appear in the corpus (possibly with sentence splitting).", t)
        loop(source.tail, selected)
      }
    }

    val selected = manage(Source.fromFile(file)) { s =>
      loop(s.getLines().drop(1).toStream.map(convert), Nil)
    }

    outFile match {
      case Some(file) =>
        val writer = new PrintWriter(file)
        selected.foreach(writer.println)
        writer.close
      case None =>
        selected.foreach(println)
    }
  }

  def convert(line: String) = {
    NGram.fromWords(line.split("<>").dropRight(1): _*)
  }

  def checkWithFiles(source: Path, ngram: NGram) = {
    def check(p: Path) = {
      manage(Source.fromFile(p.toFile)("UTF-8")) { s =>
        s.getLines().map(Preprocessor.tokenizeBySpace)
          .exists(_.sliding(2).exists(_ == ngram.words))
      }
    }

    val files = Convert.getFiles(source)
    files.exists(check)
  }
}

object NSPCheck {
  def main(args: Array[String]) {
    val seedTokens = SeedTokens.read(args(0))("UTF-8")
    val dictionary = Dictionary.read(args(1))

    println(seedTokens.tokens.size)

    seedTokens.tokens.foreach { t =>
      if (!dictionary.map.contains(t))
        println(s"${t} is not contained.")
    }

  }
}
