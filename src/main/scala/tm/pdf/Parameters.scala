package tm.pdf

import tm.text.DataConverter
import tm.text.WordSelector

object Parameters {
  object implicits {
    implicit val settings =
      DataConverter.Settings(maxN = 3, minCharacters = 3,
        selectWords = WordSelector.byTfIdf(3, 0, .25, 20000))
  }
}