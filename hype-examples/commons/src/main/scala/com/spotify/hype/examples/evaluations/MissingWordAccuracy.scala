package com.spotify.hype.examples.evaluations

import breeze.linalg._
import breeze.linalg.functions.cosineDistance
import breeze.stats._
import breeze.stats.distributions.{Multinomial, RandBasis}

import scala.collection.mutable
import scala.io.Source

/**
  * Take one word out of a sentence.
  * Draw a word at random (weighted by frequency of occurrence)
  * => How well can the model tell which one of the 2 words comes from the sentence?
  **/
object MissingWordAccuracy {

  def eval(wordEmbeddings: String,
           testSet: String): String = {

    // Parse vectors
    val wordVecs = Source.fromFile(wordEmbeddings)
      .getLines()
      .map(line => {
        val tokens = line.split("\\s", 2)
        print(tokens)
        tokens(0) -> DenseVector(tokens(1).split("\\s").map(_.toDouble))
      }).toMap

    // Count words
    val wordCnt = Source.fromFile(testSet)
      .getLines()
      .flatMap(_.split("\\W+"))
      .foldLeft(Map.empty[String, Int]) {
        (count, word) => count + (word -> (count.getOrElse(word, 0) + 1))
      }
      .filterKeys(wordVecs.contains)
    val words = wordCnt.keys.toList
    val totalWords = wordCnt.values.sum
    implicit val basis: RandBasis = RandBasis.withSeed(42)
    val wordMult = new Multinomial(
      DenseVector(wordCnt.map(_._2 / totalWords.toDouble).toArray))

    // Missing word accuracy
    val guesses = mutable.ListBuffer[Float]()
    for (lines <- Source.fromFile(testSet).getLines().grouped(1024)) {
      for(line <- lines.par) {
        val sentence = line.split("\\W+").filter(wordVecs.contains).toList
        var rightGuesses = 0
        if (sentence.length >= 2) {
          for (word <- sentence) {
            val sentenceVec = sum(sentence.map(wordVecs))
            val rightGuess = if (MissingWordAccuracy.mkGuess(sentenceVec,
              wordVecs(word),
              wordVecs(words(wordMult.sample)))) 1
            else 0
            rightGuesses += rightGuess
          }
          guesses += rightGuesses / sentence.length.toFloat
        }
      }
    }

    // Some descriptive stats
    val d = DenseVector(guesses.toArray)
    s"""
       |{
       | "#sentences": ${guesses.length},
       | "mean": ${mean(d)},
       | "stddev": ${stddev(d)},
       | "median": ${median(d)}
       |}
    """.stripMargin
  }

  def mkGuess(sentenceVec: DenseVector[Double],
              targetWordVec: DenseVector[Double],
              randomWordVec: DenseVector[Double]): Boolean = {
    val contextVec = sentenceVec - targetWordVec
    cosineDistance(targetWordVec, contextVec) < cosineDistance(randomWordVec, contextVec)
  }
}
