hype
====

[![Build Status](https://img.shields.io/circleci/project/github/spotify/hype/master.svg)](https://circleci.com/gh/spotify/hype)
[![codecov.io](https://codecov.io/github/spotify/hype/coverage.svg?branch=master)](https://codecov.io/github/spotify/hype?branch=master)
[![GitHub license](https://img.shields.io/github/license/spotify/hype.svg)](./LICENSE)

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

See example [`Dockerfile`](hype-docker/Dockerfile)

## Submit

Create a hype `Submitter` and use `runOnCluster(fn, env)` to run a closure in your docker container
environment on a Kubernetes cluster. This example runs a simple block of code that just extracts
the operating system name and lists all environment variables.

```scala
import com.spotify.hype._
import scala.sys.process._

// A simple model for describing the runtime environment
case class EnvVar(name: String, value: String)
case class Res(uname: String, vars: List[EnvVar])

def extractEnv: Res = {
  val uname = "uname -a" !!
  val vars = for ((name, value) <- sys.env.toList)
      yield EnvVar(name, value)

  Res(uname, vars)
}

// Use a Google Cloud Container Engine managed cluster
val cluster = containerEngineCluster(
    "gcp-project-id", "gce-zone-id", "gke-cluster-id") // modify these

val env = environment("gcr.io/gcp-project-id/env-image") // the env image we created earlier
    .withSecret("gcp-key", "/etc/gcloud") // a pre-created k8s secret volume named "gcp-key"

withSubmitter(cluster, "gs://my-staging-bucket") { submitter =>
  val res = submitter.runOnCluster(extractEnv, env)

  println(res.uname)
  res.vars.foreach(println)
}
```

The `res.vars` list returned should contain the environment variables that were present in the
docker container while running on the cluster. Here's the output:

```
[info] Uploading 71 files to staging location gs://my-staging-bucket/spotify-hype-staging to prepare for execution.
[info] Uploading complete: 2 files newly uploaded, 69 files cached
[info] Submitting gs://my-staging-bucket/spotify-hype-staging/manifest-oygpxf8x.txt to RunEnvironment{image=gcr.io/gcp-project-id/env-image, secretMount=Secret{name=gcp-key, mountPath=/etc/gcloud}, volumeMounts=[], resourceRequests={}}
[info] Created pod hype-run-cv7cln6y
[info] Pod hype-run-cv7cln6y assigned to node gke-hype-test-default-pool-e1122946-fg9k
[info] Kubernetes pod hype-run-cv7cln6y exited with status Succeeded
[info] Got termination message: gs://my-staging-bucket/spotify-hype-staging/continuation-3146633219315417117-knejMgGDzXTGnCqrYzeDzQ-hype-run-cv7cln6y-return.bin
[info]
[info] Linux hype-run-cv7cln6y 4.4.21+ #1 SMP Fri Feb 17 15:34:45 PST 2017 x86_64 GNU/Linux
[info] EnvVar(HYPE_EXECUTION_ID,hype-run-cv7cln6y)
[info] EnvVar(GOOGLE_APPLICATION_CREDENTIALS,/etc/gcloud/key.json)
[info] EnvVar(HOSTNAME,hype-run-cv7cln6y)
[info] EnvVar(PATH,/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin)
[info] EnvVar(JAVA_VERSION,8u121)
[info] EnvVar(KUBERNETES_SERVICE_HOST,xx.xx.xx.xx)
...
```

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
import com.spotify.hype._
import scala.sys.process._

// Create a 10Gi volume from the 'gce-ssd-pd' storage class
val ssd10Gi = volumeRequest("gce-ssd-pd", "10Gi")
val mount = "/usr/share/volume" 

def write: Int = {
  // get a random word and store it in the volume
  s"curl -so $mount/word http://www.setgetgo.com/randomword/get.php" !
}

def read: String = {
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

The submissions from the parallel stream will each run concurrently in a separate pod and have
read-only access to the `/usr/share/volume` mount. The volume should contain whatever was written
to it from the first submission.

Coordinating metadata and parameters across all submission runs should be just as trivial as
passing values from function calls into other function closure.

## Load env pod from YAML

Even though the `environment(<image>, ...)` builder can be convenient for simple cases, sometimes
more control over the Kubernetes Pod is desired. For these cases a regular Pod YAML file can be
used as a base for the `RunEnvironment`. Hype will still manage any used Volume Claims and
mounts, but will leave all other details as you've specified them.

Hype will expect at least these fields to be specified:

- `spec.containers[name:hype-run]` - There must at least be a container named `hype-run`
- `spec.containers[name:hype-run].image`  - The container must have an image specified

_Hype will override the `spec.containers[name:hype-run].args` field, so don't set it._

Here's a minimal Pod YAML file with some custom settings, `./src/main/resources/pod.yaml`:

```yaml
apiVersion: v1
kind: Pod

spec:
  restartPolicy: Never # do not retry on failure

  containers:
  - name: hype-run
    image: us.gcr.io/my-project/hype-runner:7
    imagePullPolicy: Always # pull the image on each run

    env: # additional environment variables
    - name: EXAMPLE
      value: my-env-value
```

Any resource requests added through the `RunEnvironment` API will merge with, and override the ones
set in the YAML file.

Then simply load your `RunEnvironment` through

```scala
val env = RunEnvironment.fromYaml("/pod.yaml")
```

---

_This project is in early development stages, expect anything you see to change._

[Docker]: https://www.docker.com
[Kubernetes]: https://kubernetes.io/
[GCE Persistent Disk]: http://blog.kubernetes.io/2016/10/dynamic-provisioning-and-storage-in-kubernetes.html
