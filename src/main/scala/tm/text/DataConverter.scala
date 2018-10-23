package tm.text

import scala.collection.GenSeq
import java.io.PrintWriter
import scala.annotation.tailrec
import org.slf4j.LoggerFactory
import tm.util.ParMapReduce.mapReduce
import scalaz.Scalaz._
import tm.util.Data

object DataConverter {
  val logger = LoggerFactory.getLogger(DataConverter.getClass)
  object AttributeType extends Enumeration {
    val binary, numeric = Value
  }

  type TokenCounts = Map[NGram, Int]


  /**
   * For external call
   */
  def apply(name: String, documents: GenSeq[Document], maxWords: Int, concat: Int, documentMinTf: Int = 1,
      seedWords: SeedTokens = SeedTokens.Empty(), wordSelector: WordSelector = WordSelector.basic()): Data = {
    val (countsByDocuments, dictionary) = countTokensWithNGrams(name, documents, maxWords, concat, documentMinTf, seedWords, wordSelector)
    Data.fromDictionaryAndTokenCounts(dictionary, countsByDocuments.toList, name = name)
  }

//  @Deprecated
//  def convert(name: String, documents: GenSeq[Document], maxWords: Int,
//    seeds: Option[SeedTokens] = None)(implicit settings: Settings) = {
//
//    val (countsByDocuments, dictionary) =
//      countTokensWithNGrams(name, documents, null, maxWords, settings.concatenations, seeds.getOrElse(SeedTokens.Empty()), settings.wordSelector)
//
//    //    log("Converting to bow")
//    //    val bow = convertToBow(countsByDocuments, dictionary.map)
//
//    val bowConverter = toBow(dictionary.map)(_)
//
//    logger.info("Saving in ARFF format (count data)")
//    saveAsArff(name, s"${name}.arff", AttributeType.numeric,
//      dictionary.words, countsByDocuments.seq, bowConverter)
//    logger.info("Saving in HLCM format (binary data)")
//    saveAsBinaryHlcm(name, s"${name}.txt",
//      dictionary.words, countsByDocuments.seq, bowConverter)
//    logger.info("Saving in sparse data format (binary data)")
//    saveAsSparseData(s"${name}.sparse.txt", countsByDocuments.seq, dictionary.map)
//
//    logger.info("done")
//  }

  /**
   * Counts the number of tokens with the consideration of n-grams for a n
   * specified in the {@ code settings}.
   */
  def countTokensWithNGrams(name: String, documents: GenSeq[Document], 
      maxWords: Int, concat: Int, documentMinTf: Int, seeds: SeedTokens, wordSelector: WordSelector): (GenSeq[TokenCounts], Dictionary) = {

    def replaceByNGrams(ds: GenSeq[Document], check: NGram => Boolean, concat: Int = 2) = {
      ds.map(_.sentences.map(s =>
        Preprocessor.replaceByNGrams(s, check, concat)))
        .map(ss => new Document(ss))
    }

    @tailrec
    def loop(documents: GenSeq[Document], previous: Option[Dictionary],
      frequentWords: Set[NGram], n: Int): (GenSeq[Document], Dictionary) = {
      val noAppend = n == 0 || n > concat
      val last = (n == concat && noAppend) || (n > concat)

      if (!last)
        logger.info("Counting n-grams (after {} concatentations) in each document", n)
      else
        logger.info("Counting n-grams after replacing constituent tokens in each document", n)

      // construct a sequence with tokens including those in the original
      // sentence and n-grams with that are built from the original tokens after
      // concatenation
      def appendNextNGram(sentence: Sentence): Seq[NGram] = {
        if (noAppend)
          sentence.tokens
        else {
          sentence.tokens ++ buildNextNGrams(sentence.tokens, (w: NGram) =>
            previous.get.map.contains(w) || frequentWords.contains(w))
        }
      }

      // select words
      val (dictionary, currentFrequent) = {
        logger.info("Building Dictionary")
        val allWordInfo = computeWordInfo(documents, appendNextNGram, documentMinTf).sorted

        logger.info("Saving dictionary before selection")
        Dictionary.save(s"${name}.whole_dict-${n}.csv", allWordInfo)

        val (preSelected, remaining) = if(seeds.tokens.isEmpty) (IndexedSeq.empty, allWordInfo)
                                       else //allWordInfo.partition(w => seeds.tokens.contains(w.token))
                                       {
                                         val preSelected = allWordInfo.filter(w => seeds.tokens.contains(w.token)).take(maxWords)
                                         val remaining = allWordInfo.filterNot(w => preSelected.contains(w))
                                         (preSelected, remaining)
                                       }

        if (preSelected.size > 0)
          logger.info("Using {} seed tokens", preSelected.size)

        logger.info("Selecting words in dictionary")
        val (selected, frequent) =
          wordSelector.select(
            remaining, documents.size, maxWords - preSelected.size)

        val allSelected = preSelected ++ selected
        logger.info("Number of selected tokens is {}.", allSelected.size)

        logger.info("Saving dictionary after selection")
        Dictionary.save(s"${name}.dict-${n}.csv", allSelected)

        (Dictionary.buildFrom(allSelected), frequent)
      }

      val documentsWithLargerNGrams = if (noAppend)
        documents
      else {
        logger.info("Replacing constituent tokens by n-grams after {} concatenations", n)
        replaceByNGrams(documents, dictionary.map.contains)
      }

      if (last)
        (documentsWithLargerNGrams, dictionary)
      else loop(documentsWithLargerNGrams, Some(dictionary),
        frequentWords ++ currentFrequent.map(_.token).toSet, n + 1)
    }

    logger.info(
      "Using the following word selector. {}", wordSelector.description)

    val ds = {
      if(!seeds.tokens.isEmpty){
        logger.info("Replacing constituent tokens by seed tokens")
        //Seed tokens sorted in descending order of length
        val seedTokensByLength = seeds.tokens.groupBy{ token => token.words.length }.toList.sortBy(-_._1)
        var documentsWithLargerNGrams = documents
        seedTokensByLength.foreach{ case(tokenLength, tokens) =>
          documentsWithLargerNGrams = replaceByNGrams(documentsWithLargerNGrams, tokens.contains, tokenLength)
        }
        documentsWithLargerNGrams
      }else
        documents
    }

    logger.info("Extracting words")
    val (newDocuments, dictionary) = loop(ds, None, Set.empty, 0)
    (newDocuments.map(countTermFrequencies(_, documentMinTf)), dictionary)
  }

  /**
   * Counts number of tokens in the given sequence of words.
   * DocumentMinTf, the minimum term frequency within a document to consider that term exists in a document
   */
  def countTokens(tokens: Seq[NGram], documentMinTf: Int): TokenCounts = {
    if (tokens.isEmpty) {
      Map.empty
    } else {
      tokens.groupBy{x => x}.filter(_._2.size >= documentMinTf).map{wl => (wl._1 -> wl._2.size)}
      //mapReduce(tokens.par)(w => Map(w -> 1))(_ |+| _)
    }
  }

  def countTermFrequencies(d: Document, documentMinTf: Int): TokenCounts = {
    countTermFrequencies(d, _.tokens, documentMinTf)
  }

  def countTermFrequencies(d: Document,
    tokenizer: (Sentence) => Seq[NGram], documentMinTf: Int): TokenCounts = {
    countTokens(d.sentences.flatMap(tokenizer), documentMinTf)
  }

  def computeWordInfo(documents: GenSeq[Document],
    tokenizer: (Sentence) => Seq[NGram] = _.tokens, documentMinTf: Int): IndexedSeq[WordInfo] = {
    import Preprocessor._
    import tm.util.ParMapReduce._

    val (tf, df, n) = mapReduce(documents.zipWithIndex.par) { case(d, i) =>
      val tf = countTermFrequencies(d, tokenizer, documentMinTf)

      // document count (1 for each occurring token)
      val df = tf.mapValues(c => if (c > 0) 1 else 0)

      (tf, df, 1)

    } { (c1, c2) =>
      (c1._1 |+| c2._1, c1._2 |+| c2._2, c1._3 + c2._3)
    }
    
    //    // compute the counts of original tokens and new n-grams by
    //    // documents
    //    val countsByDocuments =
    //      // convert each document to tokens
    //      documents.map(_.sentences.flatMap(tokenizer)).map(countTokens)
    //
    //    val termFrequencies = sumWordCounts(countsByDocuments)
    //    val documentFrequencies = computeDocumentFrequencies(countsByDocuments)
    //    val N = countsByDocuments.size
    //
    def buildWordInfo(token: NGram, tf: Int, df: Int) =
      WordInfo(token, tf, df, computeTfIdf(tf, df, n))

    tf.keys.map { w => buildWordInfo(w, tf(w), df(w)) }.toIndexedSeq
  }
  
  /**
   * Converts data to bag-of-words representation, based on the given word
   * counts and dictionary.
   */
  def toBow(indices: Map[NGram, Int])(counts: TokenCounts): Array[Int] = {
    val values = Array.fill(indices.size)(0)
    counts.foreach { wc =>
      indices.get(wc._1).foreach { i => values(i) = wc._2 }}
    values
  }

  def convertToBow(countsByDocuments: GenSeq[TokenCounts], indices: Map[NGram, Int]) = {
    countsByDocuments.map(toBow(indices))
  }

//  @Deprecated
//  def saveAsArff(name: String, filename: String,
//    attributeType: AttributeType.Value, words: Seq[String],
//    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {
//
//    val at = attributeType match {
//      case AttributeType.binary  => "{0, 1}"
//      case AttributeType.numeric => "numeric"
//    }
//
//    val writer = new PrintWriter(filename)
//
//    writer.println(s"@RELATION ${name}\n")
//
//    words.foreach { w => writer.println(s"@ATTRIBUTE ${w} ${at}") }
//
//    writer.println("\n@DATA")
//
//    countsByDocuments.foreach { xs => writer.println(toBow(xs).mkString(",")) }
//
//    writer.close
//  }
//
//  @Deprecated
//  def saveAsBinaryHlcm(name: String, filename: String, words: Seq[String],
//    countsByDocuments: Seq[TokenCounts], toBow: (TokenCounts) => Array[Int]) = {
//    def binarize(v: Int) = if (v > 0) 1 else 0
//
//    val writer = new PrintWriter(filename)
//
//    writer.println(s"//${filename.replaceAll("\\P{Alnum}", "_")}")
//    writer.println(s"Name: ${name}\n")
//
//    writer.println(s"// ${words.size} variables")
//    words.foreach { w => writer.println(s"${w}: s0 s1") }
//    writer.println
//
//    countsByDocuments.foreach { vs =>
//      writer.println(toBow(vs).map(binarize).mkString(" ") + " 1.0")
//    }
//
//    writer.close
//  }
//
//  /**
//   * Note that new standard is document index start from 0 but not 1
//   */
//  @Deprecated
//  def saveAsSparseData(filename: String,
//    countsByDocuments: Seq[TokenCounts], indices: Map[NGram, Int]) = {
//    val writer = new PrintWriter(filename)
//
//    countsByDocuments.zipWithIndex.foreach { p =>
//      val rowId = p._2 + 1 // since the indices from zipWithIndex start with zero
//      // filter out any words not contained in the indices or those with zero counts
//      p._1.filter(tc => indices.contains(tc._1) && tc._2 > 0)
//        .foreach { tc => writer.println(s"${rowId},${tc._1}") }
//    }
//  }
  
  /**
   * Given a sequence of tokens, build the n-grams based on the tokens.  The
   * n-grams are built from two consecutive tokens.  Besides,
   * the constituent tokens must be contained in the given {@code base} dictionary.
   * It is possible that after c concatenations n-grams, where n=2^c, may appear.
   */
  def buildNextNGrams(tokens: Seq[NGram],
    shouldConsider: (NGram) => Boolean): Iterator[NGram] =
    tokens.sliding(2).filter(_.forall(shouldConsider))
      .map(NGram.fromNGrams(_))
  
  /**
   * minDf is computed when the number of documents is given.
   */
//  @Deprecated
//  class Settings(val concatenations: Int, val minCharacters: Int,
//    val wordSelector: WordSelector)
//
//  @Deprecated
//  object Settings{
//    def apply(concatenations: Int = 0, minCharacters: Int = 3,
//        minTf: Int = 6, minDf: (Int) => Int = (Int) => 6, 
//        wordSelector: WordSelector = null): Settings = {
//      val _wordSelector = wordSelector match{
//        case null => WordSelector.basic(minCharacters, minTf, minDf)
//        case _ => wordSelector
//      }
//        
//      new Settings(concatenations, minCharacters, _wordSelector)
//    }
//    
//    def apply(concatenations: Int = 0, minCharacters: Int = 3, minTf: Int = 6,
//      minDf: (Int) => Int = (Int) => 6): Settings =
//      new Settings(concatenations, minCharacters,
//        WordSelector.Basic(minCharacters, minTf, minDf))
//
//    def apply(concatenations: Int, minCharacters: Int,
//      wordSelector: WordSelector): Settings =
//      new Settings(concatenations, minCharacters, wordSelector)
//  }
}