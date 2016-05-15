package tm.util

import java.io.File
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Path
import java.nio.file.FileVisitResult._
import java.nio.file.Files
import java.util.EnumSet
import java.nio.file.FileVisitOption
import java.nio.file.Paths

object FileHelpers {
  val filenameRegex = "(?s)(.*?)(?:\\.([^.]+))?".r

  def getPath(components: String*) = components.mkString(File.separator)
  def getNameAndExtension(filename: String) = filename match {
    case filenameRegex(name, ext) => (name, ext)
  }

  /**
   * Find files with the specified extension (e.g. pdf) in the specified
   * directory.
   */
  def findFiles(directory: Path, extension: String) = {
    val files = collection.mutable.Buffer.empty[Path]
    val suffix = "." + extension
    val visitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attr: BasicFileAttributes) = {
        if (file.toString.endsWith(suffix))
          files += directory.relativize(file)

        CONTINUE
      }
    }

    Files.walkFileTree(directory,
      EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor)
    files.toVector
  }
  
  def exists(s: String) = Files.exists(Paths.get(s))
}