package com.spotify.hype

/**
  * Created by romain on 4/10/17.
  */
trait HypeModule[T] extends Serializable {

  def getFn: T

  def getImage: String
}
