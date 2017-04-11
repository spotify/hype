package com.spotify.hype.split

import java.net.URI
import java.nio.file.{Files, Paths}

import com.spotify.hype.HypeModule

/**
  * Created by romain on 4/10/17.
  */
case class GcsToLocal(gcsInputPath: String,
                      localTextFile: String) extends HypeModule[String] {

  override def getFn = {
    Files.copy(
      Paths.get(URI.create(gcsInputPath)),
      Paths.get(localTextFile)).toString
  }

  override def getImage: String = "us.gcr.io/datawhere-test/hype-runner:7"
}
