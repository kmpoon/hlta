package tm.hillary

import tm.text.DataConverter

object Parameters {
  object implicits {
    implicit val settings = DataConverter.Settings(maxN = 3, minDf = (Int) => 6)
  }
}