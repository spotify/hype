package com.spotify.hype.examples.word2vec

import java.net.URI
import java.nio.file.{Files, Paths, StandardCopyOption}

import com.spotify.hype.HypeModule
import org.slf4j.LoggerFactory

import scala.sys.process._

case class W2vParams(train: String,
                     output: String,
                     cv: String,
                     saveVocabulary: Option[String] = None,
                     readVocabulary: Option[String] = None,
                     size: Option[Int] = None,
                     window: Option[Int] = None,
                     sample: Option[Float] = None,
                     hierarchicalSoftmax: Option[Boolean] = None,
                     negativeExamples: Option[Int] = None,
                     threads: Option[Int] = None,
                     minCount: Option[Int] = None,
                     alpha: Option[Float] = None,
                     classes: Option[Int] = None,
                     binaryOutput: Option[Boolean] = None,
                     cbow: Option[Boolean] = None)

case class Word2vec(p: W2vParams) extends HypeModule[String] {

  @transient private lazy val log = LoggerFactory.getLogger(classOf[Word2vec])

  private def getW2vCmd(train: String, output: String): String = {
    s"word2vec -train $train -output $output" +
      p.saveVocabulary.map(s => " -save-vocab " + s).getOrElse("") +
      p.readVocabulary.map(s => " -read-vocab " + s).getOrElse("") +
      p.size.map(s => " -size " + s).getOrElse("") +
      p.window.map(s => " -window " + s).getOrElse("") +
      p.sample.map(s => " -sample " + s).getOrElse("") +
      p.hierarchicalSoftmax.map(s => " -hs " + s.toInt).getOrElse("") +
      p.negativeExamples.map(s => " -negative " + s).getOrElse("") +
      p.threads.map(s => " -threads " + s).getOrElse("") +
      p.minCount.map(s => " -min-count " + s).getOrElse("") +
      p.alpha.map(s => " -alpha " + s).getOrElse("") +
      p.classes.map(s => " -classes " + s).getOrElse("") +
      p.binaryOutput.map(s => " -binary " + s.toInt).getOrElse("") +
      p.cbow.map(s => " -cbow " + s.toInt).getOrElse("")
  }

  override def getFn: String = {

    val tempDirectory = Files.createTempDirectory("")

    // Download training set if non local
    val trainPath = Paths.get(URI.create(p.train))
    val train = if (trainPath.toUri.getScheme != "file") {
      val localInput = tempDirectory.resolve(trainPath.getFileName)
      log.info(s"Copying $trainPath to $localInput")
      Files.copy(trainPath, localInput)
      localInput
    } else {
      trainPath
    }

    // Intermediate output if non local
    val outputPath = Paths.get(URI.create(p.output))
    val output = if (outputPath.toUri.getScheme != "file") {
      tempDirectory.resolve(trainPath.getFileName)
    } else {
      outputPath
    }

    // Effectively run w2v
    val w2vCmd = getW2vCmd(train.toString, output.toString)
    log.info(s"Running: $w2vCmd")
    val retCode = w2vCmd !

    // Copy output if necessary
    if (outputPath.toUri.getScheme != "file") {
      log.info(s"Copying $output to $outputPath")
      Files.copy(output, outputPath, StandardCopyOption.REPLACE_EXISTING)
    }

    // Evaluate model
    val cvPath = Paths.get(URI.create(p.cv))
    MissingWordAccuracy.eval(output.toString, cvPath.toString)
  }

  override def getImage: String = "us.gcr.io/datawhere-test/hype-word2vec:7"

  private implicit def toInT(b: Boolean): Int = if (b) 1 else 0
}
