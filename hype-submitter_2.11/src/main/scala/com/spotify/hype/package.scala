package com.spotify

import com.spotify.hype.model.DockerCluster.dockerCluster
import com.spotify.hype.model.RunEnvironment

import scala.language.implicitConversions

package object hype {

  object Environment {
    def apply(image: String): model.RunEnvironment =
      model.RunEnvironment.environment(image)
  }

  object EnvironmentFromYaml {
    def apply(resourcePath: String): model.RunEnvironment =
      model.RunEnvironment.fromYaml(resourcePath)
  }

  object VolumeRequest {
    def apply(name: String, mountPath: String): model.VolumeRequest =
      model.VolumeRequest.volumeRequest(name, mountPath)
  }

  object ContainerEngineCluster {
    def apply(project: String, zone: String, cluster: String): model.ContainerEngineCluster =
      model.ContainerEngineCluster.containerEngineCluster(project, zone, cluster)
  }

  object GkeSubmitter {
    def apply(stagingLocation: String, cluster: model.ContainerEngineCluster): Submitter =
      setupShutdown(Submitter.create(stagingLocation, cluster))
  }

  object LocalSubmitter {
    def apply(keepContainer: Boolean = false,
              keepTerminationLog: Boolean = false,
              keepVolumes: Boolean = false): Submitter =
      setupShutdown(Submitter.createLocal(
        dockerCluster(keepContainer, keepTerminationLog, keepVolumes)))
  }

  implicit class HFnOps[T](val hfn: HFn[T]) extends AnyVal {
    def run(implicit submitter: Submitter, env: RunEnvironment): T = #!

    def #!(implicit submitter: Submitter, baseEnv: RunEnvironment): T =
      submitter.runOnCluster(hfn.run, hfn.env(baseEnv))

    def #!(env: RunEnvironment)(implicit submitter: Submitter): T =
      #!(submitter, env)
  }

  implicit def fnToHfn[T](fn: util.Fn[T]): HFn[T] = HFn {
    fn.run()
  }

  implicit def toHfn[A](fn: () => A): HFn[A] = HFn {
    fn()
  }

  implicit def toHfnOps[A](fn: () => A): HFnOps[A] =
    HFnOps(toHfn(fn))

  implicit def toFn[A](fn: () => A): util.Fn[A] = new util.Fn[A] {
    override def run(): A = fn()
  }

  implicit def toFnByName[A](fn: => A): util.Fn[A] = new util.Fn[A] {
    override def run(): A = fn
  }

  private def setupShutdown(submitter: Submitter): Submitter = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = submitter.close()
    }))
    submitter
  }
}
