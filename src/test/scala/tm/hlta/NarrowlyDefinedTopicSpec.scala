package tm.hlta

import java.nio.file.Paths
import tm.test.BaseSpec
import org.latlab.util.Variable
import org.latlab.model.LTM
import org.latlab.model.BeliefNode
import collection.JavaConversions._

object NDTTest {
  val papers500Data = {
    val filename = Paths.get(
      getClass.getResource("/papers-500.data.arff.gz").toURI)
      .toAbsolutePath.toString
    Reader.readData(filename)
  }
}

class NarrowlyDefinedTopicSpec extends BaseSpec {
  describe("Papers 500 data") {
    describe("Estimating LCM by counting") {
      it("should estimate the parameters for Z141 correctly") {
        val d = NDTTest.papers500Data.binary
        val v = newVar("Z141")
        val cs = Vector("parsing", "grammar").map(s => d.variables.find(_.getName == s).get)
        val m = buildLCM(v, cs)
        val m1 = FindNarrowlyDefinedTopics.estimate(v, m, d.toHLCMData)
        m1.getNodes.map(_.asInstanceOf[BeliefNode].getCpt).foreach(println)
      }
    }
  }

  def buildLCM(root: Variable, children: Seq[Variable]) = {
    FindNarrowlyDefinedTopics.buildLCM((root, None), children.map((_, None)))
  }

  def newVar(s: String) = { val v = new Variable(2); v.setName(s); v }
}