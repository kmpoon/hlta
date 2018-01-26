package tm.util

import java.io.PrintWriter

object ArffWriter {
  object AttributeType extends Enumeration {
    val binary, numeric = Value
  }

  def write(name: String, fileName: String, attributeType: AttributeType.Value, 
      variables: Seq[String], instances: Seq[Data.Instance], format: (Double) => String) = {

    val at = attributeType match {
      case AttributeType.binary => "{0, 1}"
      case AttributeType.numeric => "integer"
    }

    val writer = new PrintWriter(fileName)

    writer.println(s"@relation ${name}")
    writer.println

    variables.foreach { w => writer.println(s"@attribute ${w} ${at}") }
    writer.println
    
    writer.println("@data")

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