import sbt.Keys._

val commonSettings = Seq(
  organization := "com.spotify",
  scalaVersion := "2.11.8",
  version := "0.1.0-SNAPSHOT",
  fork := true
)

val commonLibraryDependencies = Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.5",
  "com.google.cloud" % "google-cloud-nio" % "0.12.0-alpha",
  // resolve conflicts
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.8"
)

lazy val root: Project = Project(
  "hype-examples",
  file(".")
).aggregate(
  localsplit
)

lazy val localsplit: Project = Project(
  "local-split",
  file("local-split")
).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies
)
