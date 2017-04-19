package com.spotify.hype


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

  val defaultImage = "us.gcr.io/datawhere-test/hype-runner:5"

  def apply[T](img: String)(f: => T) = new HFn[T] {
    def run: T = f

    override def image = img
  }
}
