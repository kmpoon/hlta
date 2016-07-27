// -*- mode: scala -*-

name := "HLTA"

version := "1.0"

scalaVersion := "2.10.6"

libraryDependencies ++= 
  "org.scalatest" %% "scalatest" % "2.2.6" % "test" ::
  "org.scalactic" %% "scalactic" % "2.2.6" % "test" ::
  "org.scalaz" %% "scalaz-core" % "7.2.1" ::
  "org.apache.commons" % "commons-csv" % "1.2" ::
  "commons-io" % "commons-io" % "2.4" ::
  "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" ::
  ("edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models") ::
  "org.slf4j" % "slf4j-simple" % "1.7.21" ::
  "com.google.protobuf" % "protobuf-java" % "2.6.1"  ::
//    "org.jsoup" % "jsoup" % "1.8.3" ::
//    "org.apache.opennlp" % "opennlp-tools" % "1.6.0" ::
//    "org.apache.opennlp" % "opennlp-maxent" % "3.0.3" ::
//    "org.apache.lucene" % "lucene-core" % "5.5.0" ::
//    "org.apache.lucene" % "lucene-analyzers-common" % "5.5.0" ::
  "org.apache.pdfbox" % "pdfbox" % "1.8.10" ::
  "colt" % "colt" % "1.2.0" ::
  "nz.ac.waikato.cms.weka" % "weka-stable" % "3.6.13" ::
//    "io.argonaut" %% "argonaut" % "6.1" ::
Nil


EclipseKeys.withSource := true

EclipseKeys.withJavadoc := true

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions += "-target:jvm-1.7"

// EclipseKeys.eclipseOutput := Some("target")

// Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

// To skip test during assembly
test in assembly := {}

// unmanagedClasspath in Compile += baseDirectory.value / "FastHLTA" / "bin"

// unmanagedClasspath in Test += baseDirectory.value / "FastHLTA" / "bin"

// unmanagedClasspath in Runtime += baseDirectory.value / "FastHLTA" / "bin"

assemblyMergeStrategy in assembly := {
  case PathList("java_cup", "runtime", xs @ _* )   => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
