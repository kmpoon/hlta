package tm.util

import java.io.File

object FileHelpers {
  val filenameRegex = "(?s)(.*?)(?:\\.([^.]+))?".r

  def getPath(components: String*) = components.mkString(File.separator)
  def getNameAndExtension(filename: String) = filename match {
    case filenameRegex(name, ext) => (name, ext)
  }
}