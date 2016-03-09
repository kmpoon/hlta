package hillary

object worksheet {
    println("Welcome to the Scala worksheet")     //> Welcome to the Scala worksheet
    val lines = scala.io.Source.fromFile("/Users/kmpoon/Documents/research/workspace/HillaryEmails/converted/hillary.20160226.arff").getLines
                                                  //> lines  : Iterator[String] = non-empty iterator
    val relationRegex = """(?i)@relation(?:\s+)(\S+)""".r
                                                  //> relationRegex  : scala.util.matching.Regex = (?i)@relation(?:\s+)(\S+)
    val line = lines.next                         //> line  : String = @RELATION hillary

    line match {
        case relationRegex(name) => println(name)
    }                                             //> hillary
}