package com.spotify.hype

import com.spotify.hype.model.{LoggingSidecar, RunEnvironment}

trait HypeSubmitter {

  protected def submitter: Submitter

  def submit[T](hfn: HFn[T], env: RunEnvironment, loggingSidecar: LoggingSidecar=null): T = {
    submitter.runOnCluster(hfn.run, env, hfn.image, loggingSidecar)
  }

  protected def setupShutdown(submitter: Submitter): Submitter = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = submitter.close()
    }))
    submitter
  }
}
