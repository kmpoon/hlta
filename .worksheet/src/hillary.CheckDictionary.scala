package hillary

object CheckDictionary {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(87); 
    println("Welcome to the Scala worksheet");$skip(155); 
    val dictionary = Dictionary.read(
        "/Users/kmpoon/Documents/research/workspace/HillaryEmails/" +
        "converted/hillary.20160226.dict.csv");System.out.println("""dictionary  : hillary.Dictionary = """ + $show(dictionary ));$skip(67); 
    dictionary.info.filter(_.word.endsWith("nt")).foreach(println)}
}
