package com.spotify.hype

trait HypeModule[T] extends Serializable {

  def getFn: T

  def getImage: String
}
