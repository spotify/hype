/*-
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2016 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.hype.runner;

import static com.spotify.hype.util.Util.randomAlphaNumeric;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.RunEnvironment.EnvironmentBase;
import com.spotify.hype.model.RunEnvironment.SimpleBase;
import com.spotify.hype.model.RunEnvironment.YamlBase;
import com.spotify.hype.model.Secret;
import com.spotify.hype.model.StagedContinuation;
import com.spotify.hype.model.VolumeMount;
import com.spotify.hype.model.VolumeRequest;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.norberg.automatter.AutoMatter;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DockerRunner} implementation that submits container executions to a Kubernetes cluster.
 *
 * todo: retry docker operations
 * todo: clean up all pods on exit?
 */
class KubernetesDockerRunner implements DockerRunner {

  static final String HYPE_RUN = "hype-run";
  static final String EXECUTION_ID = "HYPE_EXECUTION_ID";

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final int POLL_PODS_INTERVAL_SECONDS = 5;

  private final KubernetesClient client;
  private final VolumeRepository volumeRepository;

  KubernetesDockerRunner(KubernetesClient client, VolumeRepository volumeRepository) {
    this.client = Objects.requireNonNull(client);
    this.volumeRepository = Objects.requireNonNull(volumeRepository);
  }

  @Override
  public Optional<URI> run(RunSpec runSpec) {
    try {
      final Pod pod = client.pods().create(createPod(runSpec));
      final String podName = pod.getMetadata().getName();
      LOG.info("Created pod {}", podName);

      Optional<URI> uri = blockUntilComplete(podName);
      client.pods().withName(podName).delete();
      return uri;
    } catch (KubernetesClientException kce) {
      throw new RuntimeException("Failed to create Kubernetes pod", kce);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while blocking", e);
    }
  }

  private Optional<URI> blockUntilComplete(final String podName) throws InterruptedException {
    LOG.debug("Checking running statuses");

    boolean nodeAssigned = false;

    while (true) {
      final PodResource<Pod, DoneablePod> pod = client.pods().withName(podName);
      final PodStatus status = pod.get().getStatus();

      if (!nodeAssigned && pod.get().getSpec().getNodeName() != null) {
        LOG.info("Pod {} assigned to node {}", podName, pod.get().getSpec().getNodeName());
        nodeAssigned = true;
      }

      switch (status.getPhase()) {
        case "Succeeded":
          LOG.info("Kubernetes pod {} exited with status {}", podName, status.getPhase());

          final Optional<ContainerStatus> containerStatus = status.getContainerStatuses().stream()
              .filter(c -> HYPE_RUN.equals(c.getName()))
              .findFirst();

          final Optional<String> terminated = containerStatus
              .flatMap(s -> Optional.ofNullable(s.getState().getTerminated()))
              .flatMap(t -> Optional.ofNullable(t.getMessage()));

          if (terminated.isPresent()) {
            String message = terminated.get();
            LOG.info("Got termination message: {}", message);
            return Optional.of(URI.create(message));
          }
          break;

        case "Failed":
          LOG.info("Kubernetes pod {} failed with status {}", podName, status);
          return Optional.empty();

        default:
          break;
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_PODS_INTERVAL_SECONDS));
    }
  }

  @VisibleForTesting
  Pod createPod(RunSpec runSpec) {
    final String podName = HYPE_RUN + "-" + randomAlphaNumeric(8);
    final RunEnvironment env = runSpec.runEnvironment();
    final Secret secret = env.secretMount();
    final StagedContinuation stagedContinuation = runSpec.stagedContinuation();
    final List<VolumeMountInfo> volumeMountInfos = volumeMountInfos(env.volumeMounts());

    final Pod basePod = getBasePod(env.base());

    // add metadata name
    final ObjectMeta metadata = basePod.getMetadata() != null
        ? basePod.getMetadata()
        : new ObjectMeta();
    metadata.setName(podName);
    basePod.setMetadata(metadata);

    final PodSpec spec = basePod.getSpec();

    // add volumes
    spec.getVolumes()
        .add(new VolumeBuilder()
            .withName(secret.name())
            .withNewSecret()
                .withSecretName(secret.name())
            .endSecret()
            .build());
    volumeMountInfos.stream()
        .map(VolumeMountInfo::volume)
        .forEach(spec.getVolumes()::add);

    final Container container = findHypeRunContainer(basePod);

    // add volume mounts
    container.getVolumeMounts()
        .add(new VolumeMountBuilder()
            .withName(secret.name())
            .withMountPath(secret.mountPath())
            .withReadOnly(true)
            .build());
    volumeMountInfos.stream()
        .map(VolumeMountInfo::volumeMount)
        .forEach(container.getVolumeMounts()::add);

    // set args
    if (container.getArgs().size() > 0) {
      LOG.warn("Overriding " + HYPE_RUN + " container args");
    }
    container.setArgs(singletonList(stagedContinuation.manifestPath().toUri().toString()));

    // add env var
    container.getEnv()
        .add(new EnvVarBuilder()
            .withName(EXECUTION_ID)
            .withValue(podName)
            .build());

    // add resource requests
    final ResourceRequirementsBuilder resourceReqsBuilder = container.getResources() != null
        ? new ResourceRequirementsBuilder(container.getResources())
        : new ResourceRequirementsBuilder();
    for (Map.Entry<String, String> request : env.resourceRequests().entrySet()) {
      resourceReqsBuilder.addToRequests(request.getKey(), new Quantity(request.getValue()));
    }
    container.setResources(resourceReqsBuilder.build());

    return basePod;
  }

  private Pod getBasePod(EnvironmentBase base) {
    if (base instanceof SimpleBase) {
      final SimpleBase simpleBase = (SimpleBase) base;
      final String imageWithTag = simpleBase.image().contains(":")
          ? simpleBase.image()
          : simpleBase.image() + ":latest";

      final Container container = new ContainerBuilder()
          .withName(HYPE_RUN)
          .withImage(imageWithTag)
          .build();

      final PodSpec spec = new PodSpecBuilder()
          .withRestartPolicy("OnFailure") // todo: max retry limit
          .addToContainers(container)
          .build();

      return new PodBuilder()
          .withSpec(spec)
          .build();
    }

    if (base instanceof YamlBase) {
      final YamlBase yamlBase = (YamlBase) base;
      final Pod pod;
      try {
        pod = YAML_MAPPER.readValue(yamlBase.yamlPath().toFile(), Pod.class);
      } catch (IOException e) {
        throw new RuntimeException("Failed to parse YAML file " + yamlBase.yamlPath(), e);
      }

      // ensure image is set
      final Container hypeRunContainer = findHypeRunContainer(pod);
      if (hypeRunContainer.getImage() == null) {
        throw new RuntimeException("Image on " + HYPE_RUN + " container must be set");
      }

      return pod;
    }

    throw new IllegalArgumentException("Unknown EnvironmentBase type");
  }

  private VolumeMountInfo volumeMountInfo(PersistentVolumeClaim claim, VolumeMount volumeMount) {
    final String claimName = claim.getMetadata().getName();

    final Volume volume = new VolumeBuilder()
        .withName(claimName)
        .withNewPersistentVolumeClaim(claimName, volumeMount.readOnly())
        .build();

    final io.fabric8.kubernetes.api.model.VolumeMount mount = new VolumeMountBuilder()
        .withName(claimName)
        .withMountPath(volumeMount.mountPath())
        .withReadOnly(volumeMount.readOnly())
        .build();

    final String ro = volumeMount.readOnly() ? "readOnly" : "readWrite";
    LOG.info("Mounting {} {} at {}", claimName, ro, volumeMount.mountPath());

    return new VolumeMountInfoBuilder()
        .persistentVolumeClaim(claim)
        .volume(volume)
        .volumeMount(mount)
        .build();
  }

  private List<VolumeMountInfo> volumeMountInfos(List<VolumeMount> volumeMounts) {
    final Map<VolumeRequest, PersistentVolumeClaim> claims = volumeMounts.stream()
        .map(VolumeMount::volumeRequest)
        .distinct()
        .collect(toMap(identity(), volumeRepository::getClaim));

    return volumeMounts.stream()
        .map(volumeMount -> volumeMountInfo(claims.get(volumeMount.volumeRequest()), volumeMount))
        .collect(toList());
  }

  @VisibleForTesting
  static Container findHypeRunContainer(Pod pod) {
    final List<Container> containers = pod.getSpec().getContainers();
    final Optional<Container> hypeRunContainer = containers.stream()
        .filter(container -> HYPE_RUN.equals(container.getName()))
        .findFirst();

    if (!hypeRunContainer.isPresent()) {
      throw new RuntimeException("Pod spec does not contain a container named '" + HYPE_RUN + "'");
    }

    return hypeRunContainer.get();
  }

  @AutoMatter
  interface VolumeMountInfo {
    PersistentVolumeClaim persistentVolumeClaim();
    Volume volume();
    io.fabric8.kubernetes.api.model.VolumeMount volumeMount();
  }
}
