package com.spotify

import com.spotify.hype.model.DockerCluster.dockerCluster

import scala.language.implicitConversions

package object hype {

  object RunEnvironment {
    def apply(): model.RunEnvironment = model.RunEnvironment.environment()
  }

  object RunEnvironmentFromYaml {
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

//  object GkeSubmitter {
//    def apply(stagingLocation: String, cluster: model.ContainerEngineCluster): Submitter =
//      setupShutdown(Submitter.create(stagingLocation, cluster))
//  }
//
//  object LocalSubmitter {
//    def apply(keepContainer: Boolean = false,
//              keepTerminationLog: Boolean = false,
//              keepVolumes: Boolean = false): Submitter =
//      setupShutdown(Submitter.createLocal(
//        dockerCluster(keepContainer, keepTerminationLog, keepVolumes)))
//  }

  implicit def fnToHfn[T](fn: util.Fn[T]): HFn[T] = HFn(fn.run())

  implicit def toHfn[A](fn: () => A): HFn[A] = HFn(fn())

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
