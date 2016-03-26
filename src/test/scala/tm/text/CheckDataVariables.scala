package tm.text

import org.latlab.util.DataSet
import org.latlab.util.DataSetLoader
import java.io.PrintWriter
import scala.collection.SortedSet

object CheckDataVariables {
    def main(args: Array[String]) {
        val name = "aaai"

        val dictionary = Dictionary.read(s"${name}.dict-3.csv")
        val data = new DataSet(DataSetLoader.convert(s"${name}.txt"))

        val variables = data.getVariables.map(_.getName)
        write("tmp/variables.txt", variables.mkString("\n"))
        
        val words = dictionary.words
        write("tmp/dictionary.txt", words.mkString("\n"))
        
        val diff = words.diff(variables)
        write("tmp/diff.txt", diff.mkString("\n"))
    }
    
    def write(filename: String, content: String) = {
        val writer = new PrintWriter(filename)
        writer.println(content)
        writer.close
    }
}