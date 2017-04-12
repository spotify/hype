package com.spotify.hype.examples.split

import java.net.URI
import java.nio.file.{Files, Paths}

import com.spotify.hype.HypeModule

case class GcsToLocal(gcsInputPath: String,
                      localTextFile: String) extends HypeModule[String] {

  override def getFn = {
    Files.copy(
      Paths.get(URI.create(gcsInputPath)),
      Paths.get(localTextFile)).toString
  }

  override def getImage: String = "us.gcr.io/datawhere-test/hype-runner:7"
}
