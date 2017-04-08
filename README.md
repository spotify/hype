hype
====

A toolkit for seamlessly executing arbitrary JVM closures in [Docker] containers on [Kubernetes].

---

## Build env image

In order for hype to be able to execute closures in your docker images, you'll have to install
the `hype-run` command by adding the following to your `Dockerfile`:

```dockerfile
# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]
```

It is important to have exactly this `ENTRYPOINT` as the Kubernetes Pods will expect to run the
`hype-run` command.

See example [`Dockerfile`](docker/Dockerfile)

## Submit

Create a hype `Submitter` and use `runOnCluster(fn, env)` to run a closure in your docker container
environment on a Kubernetes cluster. This example runs a simple block of code that just constructs
a list of all environment variables and returns it.

```scala
// A function to run in a docker container
val fn: Fn[List[String]] = () => for {
  (key,value) <- sys.env.toList
} yield s"$key=$value"

// Use a Google Cloud Container Engine managed cluster
val cluster = ContainerEngineCluster.containerEngineCluster(
    "gcp-project-id", "gce-zone-id", "gke-cluster-id") // modify these

val env = RunEnvironment.environment(
    "gcr.io/gcp-project-id/env-image", // the env image we created earlier
    Secret.secret("gcp-key", "/etc/gcloud")) // a pre-created k8s secret volume named "gcp-key"

val submitter = Submitter.create("my-staging-bucket", cluster)
submitter.runOnCluster(fn, env)
```

The `result` list returned should contain the environment variables that were present in the
docker container while running on the cluster.

## Process overview

This describes what Hype does from a high level point of view.

<p align="center">
  <img src="https://github.com/spotify/hype/blob/master/doc/hype.png?raw=true"
       width="723"
       height="336"/>
</p>

## Persistent Volumes

Hype makes it easy to schedule persistent disk volumes across different closures in a workflow.
A typical pattern seen in many use cases is to first use a disk in read-write mode to download and
prepare some data, and then fork out to several parallel tasks that use the disk in read-only mode.

<p align="center">
  <img src="https://github.com/spotify/hype/blob/master/doc/hype-volumes.png?raw=true"
       width="406"
       height="213"/>
</p>

In this example, we're using a StorageClass for [GCE Persistent Disk] that we've already set up on
our cluster.

```yaml
kind: StorageClass
apiVersion: storage.k8s.io/v1beta1
metadata:
  name: gce-ssd-pd
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-ssd
```

We can then request volumes from this StorageClass using the Hype API:

```scala
import scala.sys.process._

// Create a 10Gi volume from the 'gce-ssd-pd' storage class
val ssd10Gi = VolumeRequest.volumeRequest("gce-ssd-pd", "10Gi")
val mount = "/usr/share/volume" 

val write: Fn[Int] = () => {
  // get a random word and store it in the volume
  s"curl -so $mount/word http://www.setgetgo.com/randomword/get.php" !
}

val read: Fn[String] = () => {
  // read the word file
  s"cat $mount/word" !!
}

val readWriteEnv = environment.withMount(ssd10Gi.mountReadWrite(mount))
submitter.runOnCluster(write, readWriteEnv)

// Run 10 parallel functions that have read only access to the volume
val readOnlyEnv = environment.withMount(ssd10Gi.mountReadOnly(mount))
val results = for (_ <- Range(0, 10).par)
    yield submitter.runOnCluster(read, readOnlyEnv)
```

This submissions from the parallel stream will all run in a separate pod and have read-only
access to the `/usr/share/volume` mount. The volume should contain whatever was written to it
from the first submission.

Coordinating metadata and parameters across all submission runs should be just as trivial as
passing values from function calls into other function closure.

---

_This project is in early development stages, expect anything you see to change._

[Docker]: https://www.docker.com
[Kubernetes]: https://kubernetes.io/
[GCE Persistent Disk]: http://blog.kubernetes.io/2016/10/dynamic-provisioning-and-storage-in-kubernetes.html
