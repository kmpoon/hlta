package tm.util

import scala.io.Source

object manage {
  def apply[T](source: => Source)(f: Source => T, h: Exception => T = (ex: Exception) => throw ex) = {
    var res: Option[Source] = None
    try {
      res = Some(source)
      f(res.get)
    } catch {
      case ex: Exception => h(ex)
    } finally {
      for (s <- res) s.close
    }
  }
}