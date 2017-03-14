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

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent;
import io.fabric8.kubernetes.api.model.PodSpecFluent;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DockerRunner} implementation that submits container executions to a Kubernetes cluster.
 */
class KubernetesDockerRunner implements DockerRunner {

  private static final String NAMESPACE = "default";
  private static final String HYPE_RUN = "hype-run";
  private static final String EXECUTION_ID = "HYPE_EXECUTION_ID";
  private static final int POLL_PODS_INTERVAL_SECONDS = 60;

  private final KubernetesClient client;

  KubernetesDockerRunner(KubernetesClient client) {
    this.client = Objects.requireNonNull(client).inNamespace(NAMESPACE);
  }

  @Override
  public String run(RunSpec runSpec) throws IOException {
    try {
      final Pod pod = client.pods().create(createPod(runSpec));
      final String podName = pod.getMetadata().getName();
      blockUntilComplete(podName);
      client.pods().withName(podName).delete();
      return podName;
    } catch (KubernetesClientException kce) {
      throw new IOException("Failed to create Kubernetes pod", kce);
    } catch (InterruptedException e) {
      throw new IOException("Interrupted while blocking", e);
    }
  }

  private static Pod createPod(RunSpec runSpec) {
    final String imageWithTag = runSpec.imageName().contains(":")
        ? runSpec.imageName()
        : runSpec.imageName() + ":latest";

    final String podName = HYPE_RUN + "-" + UUID.randomUUID().toString();

    // inject environment variables
    EnvVar envVarExecution = new EnvVar();
    envVarExecution.setName(EXECUTION_ID);
    envVarExecution.setValue(podName);

    PodBuilder podBuilder = new PodBuilder()
        .withNewMetadata()
        .withName(podName)
        .endMetadata();
    PodFluent.SpecNested<PodBuilder> spec = podBuilder.withNewSpec()
        .withRestartPolicy("Never");
    PodSpecFluent.ContainersNested<PodFluent.SpecNested<PodBuilder>> container = spec
        .addNewContainer()
            .withName(HYPE_RUN)
            .withImage(imageWithTag)
            .withArgs(ImmutableList.of(runSpec.stagingLocation(), runSpec.functionFile()))
            .withEnv(envVarExecution);

    container.endContainer();
    return spec.endSpec().build();
  }

  private void blockUntilComplete(final String podName) throws InterruptedException {
    LOG.debug("Checking running statuses");

    while (true) {

      final ClientPodResource<Pod, DoneablePod> pod = client.pods().withName(podName);
      final PodStatus status = pod.get().getStatus();

      switch (status.getPhase()) {
        case "Succeeded":
        case "Failed":
          LOG.info("Kubernetes pod {} exited with status", podName, status.getPhase());
          break;
        default:
          break;
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_PODS_INTERVAL_SECONDS));
    }
  }

  @Override
  public void close() throws IOException {
    client.close();
  }
}
