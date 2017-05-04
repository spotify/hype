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
  commons,
  localsplit,
  word2vec,
  crossValW2v,
  lexvec
)

lazy val commons : Project = project.in(file("commons")).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies ++ Seq(
    "org.scalanlp" %% "breeze" % "0.13",
    "org.scalatest" %% "scalatest" % "3.0.1"
  )
)

lazy val localsplit: Project = project.in(file("local-split")).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies
).dependsOn(
  commons
)

lazy val word2vec: Project = project.in(file("word2vec")).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies
).dependsOn(
  commons
)

lazy val lexvec: Project = project.in(file("lexvec")).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies
).dependsOn(
  commons
)

lazy val crossValW2v: Project = project.in(file("cross-val-w2v")).settings(
  commonSettings,
  libraryDependencies ++= commonLibraryDependencies ++ Seq(
    "com.spotify" % "hype-submitter" % "0.0.12-SNAPSHOT"
  )
).dependsOn(
  localsplit,
  word2vec,
  lexvec
)