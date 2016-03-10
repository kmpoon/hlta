package tm.hillary

object worksheet {
    println("Welcome to the Scala worksheet")     //> Welcome to the Scala worksheet
    val lines = scala.io.Source.fromFile("/Users/kmpoon/Documents/research/workspace/HillaryEmails/converted/hillary.20160226.arff").getLines
                                                  //> java.io.FileNotFoundException: /Users/kmpoon/Documents/research/workspace/Hi
                                                  //| llaryEmails/converted/hillary.20160226.arff (No such file or directory)
                                                  //| 	at java.io.FileInputStream.open0(Native Method)
                                                  //| 	at java.io.FileInputStream.open(FileInputStream.java:195)
                                                  //| 	at java.io.FileInputStream.<init>(FileInputStream.java:138)
                                                  //| 	at scala.io.Source$.fromFile(Source.scala:90)
                                                  //| 	at scala.io.Source$.fromFile(Source.scala:75)
                                                  //| 	at scala.io.Source$.fromFile(Source.scala:53)
                                                  //| 	at tm.hillary.worksheet$$anonfun$main$1.apply$mcV$sp(tm.hillary.workshee
                                                  //| t.scala:5)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$$anonfun$$exe
                                                  //| cute$1.apply$mcV$sp(WorksheetSupport.scala:76)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.redirected(W
                                                  //| orksheetSupport.scala:65)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.$execute(Wor
                                                  //| ksheetSupport.scala:75)
                                                  //| 	at tm.hillary.worksheet$.main(tm.hillary.worksheet.scala:3)
                                                  //| 	at 
                                                  //| Output exceeds cutoff limit.
    val relationRegex = """(?i)@relation(?:\s+)(\S+)""".r
    val line = lines.next

    line match {
        case relationRegex(name) => println(name)
    }
}