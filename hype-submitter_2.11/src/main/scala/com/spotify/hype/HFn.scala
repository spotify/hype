package com.spotify.hype

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

trait HFn[T] extends Serializable {

  /**
    * The function that will run in docker
    */
  def run: T

  /**
    * Override this to allow the function to modify the container it is about to be submitted to.
    */
  def image: String = HFn.defaultImage
}

object HFn {

  val defaultImage = "spotify/hype:1"

  def apply[T](f: => T) = new HFn[T] {
    def run: T = f
  }

  def withImage[T](img: String)(f: => T) = new HFn[T] {
    def run: T = f

    override def image: String = img
  }
}
