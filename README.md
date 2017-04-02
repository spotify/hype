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

```java
// A function to run in a docker container
Fn<List<String>> fn = () -> {
  List<String> env = System.getenv().entrySet().stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .peek(System.out::println)
      .collect(Collectors.toList());

  return env;
};

// Use a Google Cloud Container Engine managed cluster
ContainerEngineCluster cluster = containerEngineCluster(
    "gcp-project-id", "gce-zone-id", "gke-cluster-id"); // modify these

RunEnvironment env = environment(
    "gcr.io/gcp-project-id/env-image", // the env image we created earlier
    secret("gcp-key", "/etc/gcloud")); // a pre-created k8s secret volume named "gcp-key"

try (Submitter submitter = Submitter.create("my-staging-bucket", cluster)) {
  List<String> result = submitter.runOnCluster(fn, env);
}
```

The `result` list returned should contain the environment variables that were present in the
docker container while running on the cluster.

## Process overview

This describes what Hype does from a high level point of view.

<p align="center">
  <img src="https://github.com/spotify/hype/blob/master/doc/hype.png?raw=true"
       width="732"
       height="356"/>
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

```java
RunEnvironment environment = environment(
    "gcr.io/gcp-project-id/env-image",
    secret("gcp-key", "/etc/gcloud"));

VolumeRequest ssd10Gi = volumeRequest("gce-ssd-pd", "10Gi");
VolumeMount usrShareRW = ssd10Gi.mountReadWrite("/usr/share/volume");
VolumeMount usrShareRO = ssd10Gi.mountReadOnly("/usr/share/volume");

try (Submitter submitter = Submitter.create("my-staging-bucket", cluster)) {
  RunEnvironment readWriteEnv = environment.withMount(usrShareRW);
  submitter.runOnCluster(fn, readWriteEnv);

  RunEnvironment readOnlyEnv = environment.withMount(usrShareRO);
  IntStream.range(0, 10)
      .parallel()
      .forEach(i -> submitter.runOnCluster(fn2, readOnlyEnv));
}
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
