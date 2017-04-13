package com.spotify.hype.examples

import java.net.URI
import java.nio.file.Paths

import com.spotify.hype.ContainerEngineCluster.containerEngineCluster
import com.spotify.hype.examples.split.LocalSplit
import com.spotify.hype.examples.word2vec.{W2vParams, Word2vec}
import com.spotify.hype.model.ResourceRequest.CPU
import com.spotify.hype.model.Secret.secret
import com.spotify.hype.model.{RunEnvironment, VolumeRequest}
import com.spotify.hype.util.Fn
import com.spotify.hype.{ContainerEngineCluster, Submitter}
import org.slf4j.LoggerFactory


object CrossValW2v {

  private val log = LoggerFactory.getLogger(CrossValW2v.getClass)

  // Mount disk
  val ssd: VolumeRequest = VolumeRequest.volumeRequest("fast", "20Gi")
  val mount: String = "/usr/share/volume"

  def run(submitter: Submitter): Unit = {

    // Download gcs file locally
    val gcsInputPath: String = "gs://hype-test/data/wiki/WestburyLab.Wikipedia.Corpus.100MB.txt"
    val localTextFile: String = Paths.get(mount)
      .resolve(Paths.get(gcsInputPath).getFileName)
      .toString

    val gcsToLocal = GSUtilCp(gcsInputPath, localTextFile)
    submitter.runOnCluster(gcsToLocal.getFn, getEnv(gcsToLocal.getImage)
      .withMount(ssd.mountReadWrite(mount)))

    // Split data
    val localTrainFile = Paths.get(mount, "train.txt").toString
    val localTestFile = Paths.get(mount, "test.txt").toString
    val localCVFile = Paths.get(mount, "cv.txt").toString

    val localSplit: HypeModule[Int] = LocalSplit(
      localTextFile, localTrainFile, localTestFile, localCVFile)

    submitter.runOnCluster(localSplit.getFn, getEnv(localSplit.getImage)
      .withMount(ssd.mountReadWrite(mount)))

    // Run w2v
    val trainingSet = URI.create("file://" + localTrainFile).toString
    val cvSet = URI.create("file://" + localCVFile).toString
    val gcsOutputDir = URI.create("gs://hype-test/output/")

    // FIXME: serializing URIs sucks!
    val cpu = 12
    val w2vParams: List[W2vParams] = List(
      W2vParams(trainingSet, gcsOutputDir.resolve("trainA.txt").toString, cvSet.toString, threads = Some(cpu), cbow = Some(true)),
      W2vParams(trainingSet, gcsOutputDir.resolve("trainB.txt").toString, cvSet.toString, threads = Some(cpu), cbow = Some(false))
    )

    val evals = for (w2vParam <- w2vParams.par;
                     w2vModule = Word2vec(w2vParam))
      yield submitter.runOnCluster(w2vModule.getFn, getEnv(w2vModule.getImage)
        .withMount(ssd.mountReadOnly(mount))
        .withRequest(CPU.of(cpu.toString)))

    evals.foreach(log.info)
  }

  def main(args: Array[String]): Unit = {
    val cluster: ContainerEngineCluster = containerEngineCluster(
      "datawhere-test", "us-east1-d", "hype-ml-test")
    val submitter: Submitter = Submitter.create("hype-test", cluster)

    try {
      run(submitter)
    } finally {
      submitter.close()
    }
  }

  def getEnv(image: String) = RunEnvironment.environment(
    image,
    secret("gcp-key", "/etc/gcloud"))


  implicit def funToFn[T](f: => T): Fn[T] = {
    new Fn[T] {
      override def run(): T = f
    }
  }

}
