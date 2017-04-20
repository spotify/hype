package com.spotify.hype

import com.spotify.hype.model.RunEnvironment

import scala.language.implicitConversions

/**
  * https://open.spotify.com/track/500h8jAdr7LvzzXlm1qxtK
  */
package object magic {

  implicit class HFnOps[T](val hfn: HFn[T]) extends AnyVal {
    def #!(implicit submitter: HypeSubmitter, env: RunEnvironment): T = submitter.submit(hfn, env)

    def #!(env: RunEnvironment)(implicit submitter: HypeSubmitter): T = #!(submitter, env)
  }

  implicit def toHfnOps[A](fn: () => A): HFnOps[A] =
    HFnOps(toHfn(fn))
}
