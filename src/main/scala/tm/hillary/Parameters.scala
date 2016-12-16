package tm.hillary

import tm.text.DataConverter

object Parameters {
  object implicits {
    implicit val settings = DataConverter.Settings(concatenations = 2, minDf = (Int) => 6)
  }
}