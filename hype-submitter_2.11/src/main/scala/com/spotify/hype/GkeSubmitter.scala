package com.spotify.hype


case class GkeSubmitter(project: String,
                        zone: String,
                        cluster: String,
                        staging: String
                       ) extends HypeSubmitter {
  private val s = setupShutdown(Submitter.create(staging,
    model.ContainerEngineCluster.containerEngineCluster(project, zone, cluster)))

  override protected def submitter: Submitter = s
}
