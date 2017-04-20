package com.spotify.hype

import com.spotify.hype.model.DockerCluster

case class LocalSubmitter(keepContainer: Boolean = false,
                          keepTerminationLog: Boolean = false,
                          keepVolumes: Boolean = false
                         ) extends HypeSubmitter {
  override protected def submitter: Submitter = setupShutdown(Submitter.createLocal(
    DockerCluster.dockerCluster(keepContainer, keepTerminationLog, keepVolumes)
  ))
}
