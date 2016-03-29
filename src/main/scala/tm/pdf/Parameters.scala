package tm.pdf

import tm.text.DataConverter

object Parameters {
    object implicits {
        implicit val settings =
            DataConverter.implicits.default.copy(maxN = 3, minDf = _ / 100)
    }
}