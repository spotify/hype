package com.spotify.hype

import com.spotify.hype.model.RunEnvironment

trait HypeSubmitter {

  protected def submitter: Submitter

  def submit[T](hfn: HFn[T], env: RunEnvironment): T = {
    submitter.runOnCluster(hfn.run, env, hfn.image)
  }

  protected def setupShutdown(submitter: Submitter): Submitter = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = submitter.close()
    }))
    submitter
  }
}
