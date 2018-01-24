package tm.hillary

object Parameters {
  object implicits {
    implicit val settings = tm.text.Convert.Settings(concatenations = 2, minDf = (Int) => 6)
  }
}