package com.spotify.hype.examples.word2vec

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

import breeze.linalg.{DenseVector, sum}
import org.scalatest._


class MissingWordAccuracyTest extends FlatSpec with Matchers {

  val vecs = Map(
    "w1" -> DenseVector(.1, .2),
    "w2" -> DenseVector(.3, .4),
    "w3" -> DenseVector(.5, .6),
    "w4" -> DenseVector(.7, .8)
  )

  val text =
    """
      |w1 w4 w2 w2 w1
      |w1 w3 w1
      |w2 w4 w1 w2
    """.stripMargin

  val tmpDir = Files.createTempDirectory("")

  val tmpVecFile = Paths.get(tmpDir.toString, "vecs.txt").toString
  val tmpTextFile = Paths.get(tmpDir.toString, "text.txt").toString

  new PrintWriter(tmpVecFile) {
    write(vecs.map { case (w, v) => s"$w ${v.toArray.mkString(" ")}" }.mkString("\n"))
    close()
  }

  new PrintWriter(tmpTextFile) {
    write(text)
    close()
  }

  it should "work :)" in {
    MissingWordAccuracy.mkGuess(sum(List("w1", "w3", "w1").map(vecs)), vecs("w3"), vecs("w1")) should be(false)
    MissingWordAccuracy.mkGuess(sum(List("w1", "w3", "w2").map(vecs)), vecs("w2"), vecs("w4")) should be(true)
    noException should be thrownBy MissingWordAccuracy.eval(tmpVecFile, tmpTextFile)
  }

  tmpDir.toFile.deleteOnExit()
}
