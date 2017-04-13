package com.spotify.hype.examples

trait HypeModule[T] extends Serializable {

  def getFn: T

  def getImage: String
}
