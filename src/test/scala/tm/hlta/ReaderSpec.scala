package tm.hlta

import tm.test.BaseSpec
import java.nio.file.Paths
import collection.JavaConversions._
import org.latlab.util.Variable
import tm.util.Reader

object ReaderSpec {
  def getFileName(name: String) =
    Paths.get(getClass.getResource(name).toURI).toAbsolutePath.toString
}

class ReaderSpec extends BaseSpec {

  def checkReferenceEqual(superset: Seq[Variable], subset: Seq[Variable]) = {
    subset.foreach { v =>
      val result = superset.exists(_.eq(v)) 
      if (!result) println(v.getName)
      result should be(true)
    }

    superset.size should be >= subset.size
  }

  describe("Data and model read from files") {
    they("should use the same variable objects") {

      val (model, data) = Reader.readLTMAndARFF(
        ReaderSpec.getFileName("/readModelDataTest.bif"),
        ReaderSpec.getFileName("/readModelDataTest.arff"))

      val modelVariables = model.getVariables.toVector
      val dataVariables = data.variables
      val names = dataVariables.map(_.getName)

      checkReferenceEqual(modelVariables, dataVariables)
//      checkReferenceEqual(modelVariables, Reader.findVariablesInModel(names, model))
    }
  }
}