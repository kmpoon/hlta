package tm.hillary

object Parameters {
  object implicits {
    implicit val settings = tm.text.DataConverter.Settings(concatenations = 2, minDf = (Int) => 6)
  }
}