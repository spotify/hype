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

import static com.spotify.hype.Util.randomAlphaNumeric;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.Secret;
import com.spotify.hype.model.StagedContinuation;
import com.spotify.hype.model.VolumeMount;
import com.spotify.hype.model.VolumeRequest;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import io.norberg.automatter.AutoMatter;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DockerRunner} implementation that submits container executions to a Kubernetes cluster.
 */
class KubernetesDockerRunner implements DockerRunner {

  private static final String HYPE_RUN = "hype-run";
  private static final String EXECUTION_ID = "HYPE_EXECUTION_ID";

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

  private Pod createPod(RunSpec runSpec) {
    final String podName = HYPE_RUN + "-" + randomAlphaNumeric(8);
    final RunEnvironment env = runSpec.runEnvironment();
    final Secret secret = env.secretMount();
    final StagedContinuation stagedContinuation = runSpec.stagedContinuation();

    final String imageWithTag = env.image().contains(":")
        ? env.image()
        : env.image() + ":latest";

    final ResourceRequirementsBuilder resourceReqsBuilder = new ResourceRequirementsBuilder();
    for (Map.Entry<String, String> request : env.resourceRequests().entrySet()) {
      resourceReqsBuilder.addToRequests(request.getKey(), new Quantity(request.getValue()));
    }
    final ResourceRequirements resources = resourceReqsBuilder.build();

    final List<VolumeMountInfo> volumeMountInfos = volumeMountInfos(env.volumeMounts());

    final Container container = new ContainerBuilder()
        .withName(HYPE_RUN)
        .withImage(imageWithTag)
        .withCommand(Collections.singletonList("hype-run"))
        .withArgs(ImmutableList.of(
            stagedContinuation.stageLocation().toString(),
            stagedContinuation.continuationFileName()))
        .addNewEnv()
            .withName(EXECUTION_ID)
            .withValue(podName)
        .endEnv()
        .withResources(resources)
        .addNewVolumeMount()
            .withName(secret.name())
            .withMountPath(secret.mountPath())
            .withReadOnly(true)
        .endVolumeMount()
        .build();

    volumeMountInfos.stream()
        .map(VolumeMountInfo::volumeMount)
        .forEach(container.getVolumeMounts()::add);

    final PodSpec spec = new PodSpecBuilder()
        .withRestartPolicy("OnFailure") // todo: max retry limit
        .addToContainers(container)
        .addNewVolume()
            .withName(secret.name())
            .withNewSecret(secret.name())
        .endVolume()
        .build();

    volumeMountInfos.stream()
        .map(VolumeMountInfo::volume)
        .forEach(spec.getVolumes()::add);

    return new PodBuilder()
        .withNewMetadata()
            .withName(podName)
        .endMetadata()
        .withSpec(spec)
        .build();
  }

  private Optional<URI> blockUntilComplete(final String podName) throws InterruptedException {
    LOG.debug("Checking running statuses");

    boolean nodeAssigned = false;

    while (true) {
      final ClientPodResource<Pod, DoneablePod> pod = client.pods().withName(podName);
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

  @AutoMatter
  interface VolumeMountInfo {
    PersistentVolumeClaim persistentVolumeClaim();
    Volume volume();
    io.fabric8.kubernetes.api.model.VolumeMount volumeMount();
  }
}
