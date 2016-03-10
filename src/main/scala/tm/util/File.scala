package tm.util

import java.io.File

object FileHelpers {
    def getPath(components: String*) = components.mkString(File.separator)
}