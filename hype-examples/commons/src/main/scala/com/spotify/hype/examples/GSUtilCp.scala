package com.spotify.hype.examples

import org.slf4j.LoggerFactory

import scala.sys.process._

case class GSUtilCp(inPath: String,
                    outPath: String) extends HypeModule[Int] {

  @transient private lazy val log = LoggerFactory.getLogger(classOf[GSUtilCp])


  override def getFn = {
    val cmd = s"gsutil -m cp -r $inPath $outPath"
    log.info(cmd)
    cmd !
  }

  override def getImage: String = "us.gcr.io/datawhere-test/hype-examples-base:4"
}
