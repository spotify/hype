package com.spotify

import com.spotify.hype.model.ContainerEngineCluster

package object hype {

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

  def withSubmitter(cluster: ContainerEngineCluster, stagingBucket: String)
                   (fn: (Submitter) => Unit): Unit = {
    val submitter = Submitter.create(stagingBucket, cluster)
    try fn(submitter)
    finally submitter.close()
  }

  def withLocalSubmitter(dockerCluster: model.DockerCluster = model.DockerCluster.dockerCluster())
                        (fn: (Submitter) => Unit): Unit = {
    val submitter = Submitter.createLocal(dockerCluster)
    try fn(submitter)
    finally submitter.close()
  }
}
