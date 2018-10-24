// -*- mode: scala -*-

name := "HLTA"

version := "2.3"

scalaVersion := "2.11.11"

classpathTypes += "maven-plugin"

libraryDependencies ++= 
//  "org.scalatest" %% "scalatest" % "2.2.6" % "test" ::
//  "org.scalactic" %% "scalactic" % "2.2.6" % "test" ::
  "org.scalaz" %% "scalaz-core" % "7.2.26" ::
  "org.apache.commons" % "commons-csv" % "1.2" ::
  "org.apache.commons" % "commons-compress" % "1.18" ::
  "org.apache.commons" % "commons-text" % "1.6" ::
  "commons-io" % "commons-io" % "2.6" ::
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" ::
  ("edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" classifier "models") ::
  "org.slf4j" % "slf4j-simple" % "1.7.21" ::
//  "com.google.protobuf" % "protobuf-java" % "2.6.1"  ::
  "org.rogach" %% "scallop" % "2.0.0" ::
//  "org.apache.spark" %% "spark-core" % "1.6.2" % "provided" ::
//  "org.apache.spark" %% "spark-mllib" % "1.6.2" % "provided" ::
//  "org.jsoup" % "jsoup" % "1.8.3" ::
//  "org.apache.opennlp" % "opennlp-tools" % "1.6.0" ::
//  "org.apache.opennlp" % "opennlp-maxent" % "3.0.3" ::
//  "org.apache.lucene" % "lucene-core" % "5.5.0" ::
//  "org.apache.lucene" % "lucene-analyzers-common" % "5.5.0" ::
  "org.apache.pdfbox" % "pdfbox" % "2.0.12" ::
  "colt" % "colt" % "1.2.0" ::
  "nz.ac.waikato.cms.weka" % "weka-stable" % "3.6.13" ::
//  "org.scala-lang.modules" %% "scala-xml" % "1.0.6" ::
//  "io.argonaut" %% "argonaut" % "6.1" ::
// https://mvnrepository.com/artifact/org.deeplearning4j
//  "org.nd4j" % "nd4j-native-platform" % "0.9.1" ::
//  "org.deeplearning4j" % "deeplearning4j-core" % "0.9.1" ::
//  "org.deeplearning4j" % "deeplearning4j-nlp" % "0.9.1" ::
  "org.json4s" %% "json4s-native" % "3.6.0-M2" ::
  "com.github.tototoshi" %% "scala-csv" % "1.3.5" ::
//  "junit" % "junit" % "4.11" ::
// dependecies for fnlp
  "net.sf.trove4j" % "trove4j" % "3.0.3" ::
  "commons-cli" % "commons-cli" % "1.2" ::
Nil


EclipseKeys.withSource := true

EclipseKeys.withJavadoc := true

javacOptions ++= Seq("-encoding", "UTF-8")

// EclipseKeys.eclipseOutput := Some("target")

// Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

assemblyJarName in assembly := "HLTA.jar"

assemblyOption in assembly :=
  (assemblyOption in assembly).value.copy(
    includeScala = false, includeDependency = false)

// To skip test during assembly
test in assembly := {}

assemblyMergeStrategy in assembly := {
  case PathList("java_cup", "runtime", xs @ _* )   => MergeStrategy.first
  case PathList("EDU", "oswego", "cs", "dl", xs @ _* )   => MergeStrategy.first
  case PathList("edu","umd","cs","findbugs","annotations", xs @ _* )   => MergeStrategy.first
  case PathList("it","unimi","dsi","fastutil", xs @ _* )   => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}


// unmanagedClasspath in Compile += baseDirectory.value / "FastHLTA" / "bin"

// unmanagedClasspath in Test += baseDirectory.value / "FastHLTA" / "bin"

// unmanagedClasspath in Runtime += baseDirectory.value / "FastHLTA" / "bin"
