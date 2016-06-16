package tm.util

import java.io.PrintWriter

object ArffWriter {
  object AttributeType extends Enumeration {
    val binary, numeric = Value
  }

  def write(name: String, fileName: String, attributeType: AttributeType.Value,
    variables: Seq[String], instances: Seq[Data.Instance],
    format: (Double) => String) = {

    val at = attributeType match {
      case AttributeType.binary => "{0, 1}"
      case AttributeType.numeric => "numeric"
    }

    val writer = new PrintWriter(fileName)

    writer.println(s"@RELATION ${name}")
    writer.println

    variables.foreach { w => writer.println(s"@ATTRIBUTE ${w} ${at}") }
    writer.println
    
    writer.println("@DATA")

    instances.foreach { instance =>
      writer.print(instance.values.map(format).mkString(","))

      if (instance.weight == 1) {
        writer.println
      } else {
        writer.println(s", {${instance.weight}}")
      }
    }

    writer.close
  }

}