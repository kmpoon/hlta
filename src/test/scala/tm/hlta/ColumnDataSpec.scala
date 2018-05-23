package tm.hlta

import tm.test.BaseSpec
import org.latlab.util.Variable
import tm.util.Data

class ColumnDataSpec extends BaseSpec {

  trait ColumnsData {
    val variables = Vector.fill(3)(new Variable(2))
    val columns = (variables(0), List(1, 5)) +:
      (variables(1), List(2, 5, 7)) +: (variables(2), Nil) +: Nil
    val weights = Vector.fill(10)(0.0)
    val data = FindNarrowlyDefinedTopics.convertToData(columns, weights)
  }
  
  trait ColumnDataWithCheck extends ColumnsData {
    def check(i: Int)(expected: Double*) = {
      data.instances(i).denseValues(data.variables.size) should equal(Vector(expected: _*))
    }
  }

  describe("Data converted from a list of sample columns") {
    it("should be have the correct length") {
      new ColumnDataWithCheck {
        data.instances.length should equal(10)
      }
    }
    it("should have the correct zero entries") {
      new ColumnDataWithCheck {
        for (i <- Seq(0, 3, 4, 6, 8, 9)) {
          check(i)(0, 0, 0)
        }
      }
    }
    it("should have the correct instances with non-zero entries") {
      new ColumnDataWithCheck {
        check(1)(1, 0, 0)
        check(2)(0, 1, 0)
        check(5)(1, 1, 0)
        check(7)(0, 1, 0)
      }
    }
  }
}