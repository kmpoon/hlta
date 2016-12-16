package tm.hlta

import tm.util.Arguments
import java.nio.file.Paths
import java.nio.file.Files

object ExtractTopics {
  class Conf(args: Seq[String]) extends Arguments(args) {
    banner("Usage: tm.hlta.ExtractTopics [OPTION]... name model")
    val outputDirectory = opt[String](default = Some("topic_output"),
      descr = "Output directory for extracted topic files (default: topic_output)")
    val title = opt[String](default = Some("Topic Tree"), descr = "Title in the topic tree")
    val name = trailArg[String](descr = "Name of files to be generated")
    val model = trailArg[String](descr = "Name of model file (e.g. model.bif)")

    verify
    checkDefaultOpts()
  }

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val output = Paths.get(conf.outputDirectory())
    if (!Files.exists(output))
      Files.createDirectories(output)

    clustering.HLTAOutputTopics_html_Ltm.main(
      Array(conf.model(), conf.outputDirectory(), "no", "no", "7"));

    val topicFile = output.resolve("TopicsTable.html")
    RegenerateHTMLTopicTree.run(topicFile.toString(), conf.name(), conf.title())
  }
}