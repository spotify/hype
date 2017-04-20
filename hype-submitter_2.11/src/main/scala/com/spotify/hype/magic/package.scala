package com.spotify.hype

import com.spotify.hype.model.RunEnvironment

import scala.language.implicitConversions

/**
  * https://open.spotify.com/track/500h8jAdr7LvzzXlm1qxtK
  */
package object magic {

  implicit class HFnOps[T](val hfn: HFn[T]) extends AnyVal {
    def run(implicit submitter: Submitter, env: RunEnvironment): T = #!

    def #!(implicit submitter: Submitter, env: RunEnvironment): T =
      submitter.runOnCluster(hfn.run, env, hfn.image)

    def #!(env: RunEnvironment)(implicit submitter: Submitter): T =
      #!(submitter, env)
  }

  implicit def toHfnOps[A](fn: () => A): HFnOps[A] =
    HFnOps(toHfn(fn))

}
