package com.spotify.hype.examples.split

import com.spotify.hype.examples.HypeModule

import scala.sys.process._

case class LocalSplit(inputFile: String,
                      trainFile: String,
                      cvFile: String,
                      testFile: String,
                      trainPct: Float = .7f,
                      cvPct: Float = .2f,
                      testPct: Float = .1f) extends HypeModule[Int] {

  override def getImage: String = "us.gcr.io/datawhere-test/split:5"

  override def getFn = {
    s"python /kisssplit.py $inputFile $trainPct $testPct $cvPct $trainFile $testFile $cvFile" !
  }
}
