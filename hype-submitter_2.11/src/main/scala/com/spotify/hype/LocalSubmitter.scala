package com.spotify.hype

case class LocalSubmitter() extends HypeSubmitter {
  override protected def submitter: Submitter = Submitter.createLocal
}
