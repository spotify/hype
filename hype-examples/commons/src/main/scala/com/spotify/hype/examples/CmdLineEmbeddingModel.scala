package com.spotify.hype.examples

import java.nio.file.{Files, Paths}

import com.spotify.hype.examples.evaluations.MissingWordAccuracy
import org.slf4j.LoggerFactory

import scala.sys.process._


trait CmdLineEmbeddingModel {

  @transient private lazy val log = LoggerFactory.getLogger(classOf[CmdLineEmbeddingModel])

  def cmd(train: String, output: String): String

  def run(localInput: String, gcsOutput: String, localCv: String): Seq[(String, String)] = {
    val tempDirectory = Files.createTempDirectory("")

    val localOutput = tempDirectory.resolve(Paths.get(localInput).getFileName).toString

    val modelCmd = cmd(localInput, localOutput)
    log.info(s"Running: $modelCmd")
    val retCode = modelCmd !

    log.info(s"Copying $localOutput to $gcsOutput")
    GSUtilCp(localOutput, gcsOutput).getFn

    log.info(s"Running evaluation...")
    List("cmd" -> modelCmd) ++ MissingWordAccuracy.eval(localOutput, localCv)
  }
}
