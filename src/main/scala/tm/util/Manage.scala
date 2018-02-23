package tm.util

import scala.io.Source

object manage {
  import scala.language.reflectiveCalls

  def apply[S <: { def close(): Unit }, T](source: => S)(f: S => T, h: Exception => T = (ex: Exception) => throw ex) = {
    var res: Option[S] = None
    try {
      res = Some(source)
      f(res.get)
    } catch {
      case ex: Exception => h(ex)
    } finally {
      for (s <- res) s.close()
    }
  }
}