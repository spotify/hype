package com.spotify.hype


case class GkeCluster(project: String,
                      zone: String,
                      cluster: String,
                      staging: String
                     ) extends HypeCluster with AutoCloseable {

  private val s = Submitter.create(staging,
    ContainerEngineCluster(project, zone, cluster))

  override protected def submitter: Submitter = s

  override def close(): Unit = submitter.close()
}
