package tm.util

object HlcmWriter {
  /**
   * Copied from Leonard Poon's code
   */
//  def write(name: String, filename: String, variables: Seq[String], instances: Seq[Data.Instance], toBow: (TokenCounts) => Array[Int]) = {
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
}