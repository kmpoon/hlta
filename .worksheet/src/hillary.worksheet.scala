package hillary

object worksheet {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(81); 
    println("Welcome to the Scala worksheet");$skip(142); 
    val lines = scala.io.Source.fromFile("/Users/kmpoon/Documents/research/workspace/HillaryEmails/converted/hillary.20160226.arff").getLines;System.out.println("""lines  : Iterator[String] = """ + $show(lines ));$skip(58); 
    val relationRegex = """(?i)@relation(?:\s+)(\S+)""".r;System.out.println("""relationRegex  : scala.util.matching.Regex = """ + $show(relationRegex ));$skip(26); 
    val line = lines.next;System.out.println("""line  : String = """ + $show(line ));$skip(74); 

    line match {
        case relationRegex(name) => println(name)
    }}
}
