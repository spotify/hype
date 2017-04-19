package com.spotify.hype

case class LocalCluster() extends HypeCluster {
  override protected def submitter: Submitter = Submitter.createLocal
}
