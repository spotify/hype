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

Submitter submitter = Submitter.create('my-staging-bucket', cluster);

List<String> result = submitter.runOnCluster(fn, env);
```

The `result` list returned should contain the environment variables that were present in the
docker container while running on the cluster.

---

_This project is in early development stages, expect anything you see to change._

[Docker]: https://www.docker.com
[Kubernetes]: https://kubernetes.io/
