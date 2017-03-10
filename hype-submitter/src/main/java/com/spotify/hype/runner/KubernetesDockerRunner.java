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
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpecFluent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.jdo.spi.StateManager;

/**
 * A {@link DockerRunner} implementation that submits container executions to a Kubernetes cluster.
 */
class KubernetesDockerRunner implements DockerRunner {

  static final String NAMESPACE = "default";
  static final String STYX_RUN = "styx-run";
  static final String HYPE_EXECUTION_INSTANCE_ANNOTATION = "hype-execution-instance";
  static final int POLL_PODS_INTERVAL_SECONDS = 60;
  static final String EXECUTION_ID = "HYPE_EXECUTION_ID";

  private final KubernetesClient client;

  private Watch watch;

  KubernetesDockerRunner(KubernetesClient client) {
    this.client = Objects.requireNonNull(client).inNamespace(NAMESPACE);
  }

  @Override
  public String run(RunSpec runSpec) throws IOException {
    try {
      final Pod pod = client.pods().create(createPod(runSpec));
      return pod.getMetadata().getName();
    } catch (KubernetesClientException kce) {
      throw new IOException("Failed to create Kubernetes pod", kce);
    }
  }

  private static Pod createPod(RunSpec runSpec) {
    final String imageWithTag = runSpec.imageName().contains(":")
        ? runSpec.imageName()
        : runSpec.imageName() + ":latest";

    final String podName = STYX_RUN + "-" + UUID.randomUUID().toString();

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
            .withName(STYX_RUN)
            .withImage(imageWithTag)
            .withArgs(ImmutableList.of(runSpec.stagingLocation(), runSpec.functionFile()))
            .withEnv(envVarExecution);

    container.endContainer();
    return spec.endSpec().build();
  }

  @Override
  public void cleanup(String executionId) {
    client.pods().withName(executionId).delete();
  }

  @Override
  public void close() throws IOException {
    if (watch != null) {
      watch.close();
    }
  }


  void pollPods() {
    try {
      final PodList list = client.pods().list();
      examineRunningWFISandAssociatedPods(list);

      final int resourceVersion = Integer.parseInt(list.getMetadata().getResourceVersion());

      for (Pod pod : list.getItems()) {
        logEvent(Watcher.Action.MODIFIED, pod, resourceVersion, true);
        inspectPod(Watcher.Action.MODIFIED, pod);
      }
    } catch (Throwable t) {
      LOG.warn("Error while polling pods", t);
    }
  }

  private void inspectPod(Watcher.Action action, Pod pod) {
    final Map<String, String> annotations = pod.getMetadata().getAnnotations();
    final String podName = pod.getMetadata().getName();
    if (!annotations.containsKey(KubernetesDockerRunner.HYPE_EXECUTION_INSTANCE_ANNOTATION)) {
      LOG.warn("Got pod without workflow instance annotation {}", podName);
      return;
    }
    final WorkflowInstance workflowInstance = WorkflowInstance.parseKey(
        annotations.get(KubernetesDockerRunner.HYPE_EXECUTION_INSTANCE_ANNOTATION));

    final RunState runState = stateManager.get(workflowInstance);
    if (runState == null) {
      LOG.warn("Pod event for unknown or inactive workflow instance {}", workflowInstance);
      return;
    }

    final Optional<String> executionIdOpt = runState.data().executionId();
    if (!executionIdOpt.isPresent()) {
      LOG.warn("Pod event for state with no current executionId: {}", podName);
      return;
    }

    final String executionId = executionIdOpt.get();
    if (!podName.equals(executionId)) {
      LOG.warn("Pod event not matching current exec id, current:{} != pod:{}",
          executionId, podName);
      return;
    }

    final List<Event> events = translate(workflowInstance, runState, action, pod);

    for (Event event : events) {
      if (event.accept(new PullImageErrorMatcher())) {
        stats.pullImageError();
      }

      try {
        stateManager.receive(event);
      } catch (StateManager.IsClosed isClosed) {
        LOG.warn("Could not receive kubernetes event", isClosed);
        throw Throwables.propagate(isClosed);
      }
    }
  }

  private void logEvent(Watcher.Action action, Pod pod, int resourceVersion,
                        boolean polled) {
    final String podName = pod.getMetadata().getName();
    final String workflowInstance = pod.getMetadata().getAnnotations()
        .getOrDefault(KubernetesDockerRunner.HYPE_EXECUTION_INSTANCE_ANNOTATION, "N/A");
    final String status = readStatus(pod);

    LOG.info("{}Pod event for {} at resource version {}, action: {}, workflow instance: {}, status: {}",
             polled ? "Polled: " : "", podName, resourceVersion, action, workflowInstance, status);
  }

  private String readStatus(Pod pod) {
    return pod.getStatus().toString();
  }

  public class PodWatcher implements Watcher<Pod> {

    private static final int RECONNECT_DELAY_SECONDS = 1;

    private int lastResourceVersion;

    PodWatcher(int resourceVersion) {
      this.lastResourceVersion = resourceVersion;
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
      if (pod == null) {
        return;
      }

      logEvent(action, pod, lastResourceVersion, false);

      try {
        inspectPod(action, pod);
      } finally {
        // fixme: this breaks the kubernetes api convention of not interpreting the resource version
        // https://github.com/kubernetes/kubernetes/blob/release-1.2/docs/devel/api-conventions.md#metadata
        lastResourceVersion = Integer.parseInt(pod.getMetadata().getResourceVersion());
      }
    }

    private void reconnect() {
      LOG.warn("Re-establishing watching from {}", lastResourceVersion);

      try {
        watch = client.pods()
            .withResourceVersion(Integer.toString(lastResourceVersion))
            .watch(this);
      } catch (Throwable e) {
        LOG.warn("Retry threw", e);
        scheduleReconnect();
      }
    }

    private void scheduleReconnect() {
      EXECUTOR.schedule(this::reconnect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onClose(KubernetesClientException e) {
      LOG.warn("Watch closed", e);

      // kube seems to gc old resource versions
      if (e != null && e.getCause() instanceof ProtocolException) {
        // todo: this is racy : more events can be purged while we're playing catch up
        lastResourceVersion++;
        reconnect();
      } else {
        scheduleReconnect();
      }
    }
}
