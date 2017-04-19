package com.spotify.hype

trait HFn[T] extends Serializable {

  /**
    * The function that will run in docker
    */
  def run: T

  /**
    * Override this to allow the function to modify the environment it is about to be submitted to.
    *
    * @param baseEnv The original environment used for the submission
    * @return an optionally modified environment that will ultimately be used for the submission
    */
  def image: String = "hype/base-image:1"
}

object HFn {
  def apply[T](f: => T) = new HFn[T] {
    def run: T = f
  }
}
