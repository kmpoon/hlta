package tm.text

import scala.io.Source

object StopWords {
    def read(filename: String): Set[String] = {
        Source.fromFile(filename).getLines.filter(_.size > 0).toSet
    }
}