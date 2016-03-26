package tm.pdf

import java.io.File
import scala.collection.JavaConversions._
import tm.util.FileHelpers
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult._
import java.io.IOException
import org.apache.commons.io.FilenameUtils

object CleanFileNames {
    def main(args: Array[String]) {
        if (args.length < 1)
            println("CleanFileNames directory")
        else
            clean(args(0))
    }

    class Renamer extends SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attr: BasicFileAttributes) = {
            val parent = file.getParent
            val name = FilenameUtils.getBaseName(file.toString)
            val extension = FilenameUtils.getExtension(file.toString)

            val newName = process(name)

            rename(parent, s"${name}.${extension}", s"${newName}.${extension}")
            //            println(file.toString())
            CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException) = {
            val parent = dir.getParent
            val name = dir.getFileName.toString
            val newName = process(name.toString)

            rename(parent, name, newName)

            CONTINUE
        }
    }

    def rename(parent: Path, name: String, newName: String) = {
        if (name != newName) {
            println(parent.resolve(name))
            Files.move(parent.resolve(name), parent.resolve(newName))
        }
    }

    def process(name: String) = {
        name.replaceAll("\\n", " ")
            .replaceAll("[\\s ]+", " ")
            .replaceAll("[:/]", "-")
            .trim
    }

    def clean(directory: String): Unit = {
        val path = Paths.get(directory)
        Files.walkFileTree(path, new Renamer)
    }
}