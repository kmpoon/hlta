// -*- mode: scala -*-

name := "TextMining"

version := "1.0"

scalaVersion := "2.10.6"

libraryDependencies ++= 
	"org.scalatest" %% "scalatest" % "2.2.6" % "test" ::
    "org.scalactic" %% "scalactic" % "2.2.6" ::
    "org.apache.commons" % "commons-csv" % "1.2" ::
    "org.apache.opennlp" % "opennlp-tools" % "1.6.0" ::
    "org.apache.opennlp" % "opennlp-maxent" % "3.0.3" ::
    "org.apache.lucene" % "lucene-core" % "5.5.0" ::
    "org.apache.lucene" % "lucene-analyzers-common" % "5.5.0" ::
    "org.apache.pdfbox" % "pdfbox" % "1.8.10" ::
	Nil


EclipseKeys.withSource := true

EclipseKeys.withJavadoc := true

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions += "-target:jvm-1.6"

// EclipseKeys.eclipseOutput := Some("target")

// Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

// To skip test during assembly
// test in assembly := {}

unmanagedClasspath in Compile += baseDirectory.value / "FastHLTA" / "bin"

unmanagedClasspath in Test += baseDirectory.value / "FastHLTA" / "bin"

unmanagedClasspath in Runtime += baseDirectory.value / "FastHLTA" / "bin"
