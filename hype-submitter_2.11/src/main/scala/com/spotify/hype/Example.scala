package com.spotify.hype

import com.spotify.hype.model.ResourceRequest.{CPU, MEMORY}
import com.spotify.hype.model.VolumeRequest._

import scala.collection.JavaConverters._

private object Example {

  def main(args: Array[String]): Unit = {

    val env = RunEnvironment()
      .withSecret("gcp-key", "/etc/gcloud")
      .withRequest(CPU.of("200m"))
      .withRequest(MEMORY.of("256Mi"))

    val record = ("hello", 42)

    val fn = HFn {
      val cwd = System.getProperty("user.dir")
      println(s"cwd = $cwd")
      println(s"record = $record")
      System.getenv().asScala.map { case (foo, bar) => s"$foo=$bar" }
    }

    // create a volume request from a predefined storage class with name 'slow'
    val slow10Gi = volumeRequest("slow", "10Gi")

    val submitter = GkeSubmitter("datawhere-test", "us-east1-d", "hype-test", "gs://hype-test/staging")

    val ret = submitter.submit(fn, env.withMount(slow10Gi.mountReadWrite("/usr/share/volume")))
    println(ret)

    for (i <- (0 to 10).par) {
      submitter.submit(fn, env.withMount(slow10Gi.mountReadOnly("/usr/share/volume")))
    }
  }
}
