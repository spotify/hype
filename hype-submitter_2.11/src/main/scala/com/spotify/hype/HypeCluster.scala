package com.spotify.hype

import com.spotify.hype.model.RunEnvironment

trait HypeCluster {

  protected def submitter: Submitter

  def submit[T](hfn: HFn[T], env: RunEnvironment): T = {
    val runEnv = env.withImageOverride(hfn.image)
    submitter.runOnCluster(hfn.run, runEnv)
  }
}
