package com.spotify.hype

/**
  * TODO: restrict scope to test?
  */
case class TestCluster() extends HypeCluster {
  override protected def submitter: Submitter = Submitter.createLocal
}
