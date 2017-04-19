/*-
 * -\-\-
 * hype-submitter
 * --
 * Copyright (C) 2016 - 2017 Spotify AB
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

import static com.spotify.hype.model.ResourceRequest.CPU;
import static com.spotify.hype.model.ResourceRequest.MEMORY;
import static com.spotify.hype.model.RunEnvironment.get;
import static com.spotify.hype.model.RunEnvironment.fromYaml;
import static com.spotify.hype.runner.KubernetesDockerRunner.EXECUTION_ID;
import static com.spotify.hype.runner.KubernetesDockerRunner.HYPE_RUN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.spotify.hype.gcs.RunManifest;
import com.spotify.hype.gcs.RunManifestBuilder;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.Secret;
import com.spotify.hype.model.StagedContinuation;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class KubernetesDockerRunnerTest {

  private static final Secret SECRET = Secret.secret("keys", "/etc/keys");
  private static final Path MANIFEST_PATH = Paths.get("/etc/manifest.txt");
  private static final RunManifest MANIFEST = new RunManifestBuilder()
      .continuation("foobar.bin")
      .build();

  @Rule
  public ExpectedException expect = ExpectedException.none();

  private KubernetesDockerRunner runner;

  @Before
  public void setUp() throws Exception {
    KubernetesClient client = Mockito.mock(KubernetesClient.class);
    VolumeRepository volumeRepository = Mockito.mock(VolumeRepository.class);
    runner = new KubernetesDockerRunner(client, volumeRepository);
  }

  @Test
  public void setsManifestAsArgument() throws Exception {
    RunEnvironment env = get();
    Pod pod = createPod(env);

    Container container = findHypeRunContainer(pod);
    assertThat(container.getArgs(), contains(MANIFEST_PATH.toUri().toString()));
  }

  @Test
  public void setsManifestAsArgumentWhenLoadedFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml");
    Pod pod = createPod(env);

    Container container = findHypeRunContainer(pod);
    assertThat(container.getArgs(), contains(MANIFEST_PATH.toUri().toString()));
  }

  @Test
  public void setsHypeExecIdAsEnvVar() throws Exception {
    RunEnvironment env = get();
    Pod pod = createPod(env);
    String name = pod.getMetadata().getName();

    Container container = findHypeRunContainer(pod);
    assertThat(container.getEnv(), hasItems(envVar(EXECUTION_ID, name)));
  }

  @Test
  public void setsHypeExecIdAsEnvVarWhenLoadedFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml");
    Pod pod = createPod(env);
    String name = pod.getMetadata().getName();

    Container container = findHypeRunContainer(pod);
    assertThat(container.getEnv(), hasItems(envVar(EXECUTION_ID, name)));
  }

  @Test
  public void setsResourceRequests() throws Exception {
    RunEnvironment env = get()
        .withRequest(CPU.of("250m"))
        .withRequest(MEMORY.of("2Gi"))
        .withRequest("gpu", "2");
    Pod pod = createPod(env);

    Container container = findHypeRunContainer(pod);
    ResourceRequirements resources = container.getResources();
    assertThat(resources.getRequests(), hasEntry("cpu", new Quantity("250m")));
    assertThat(resources.getRequests(), hasEntry("memory", new Quantity("2Gi")));
    assertThat(resources.getRequests(), hasEntry("gpu", new Quantity("2")));
  }

  @Test
  public void addsResourceRequestsWhenLoadedFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml")
        .withRequest(MEMORY.of("2Gi"))
        .withRequest("gpu", "2");
    Pod pod = createPod(env);

    Container container = findHypeRunContainer(pod);
    ResourceRequirements resources = container.getResources();
    assertThat(resources.getRequests(), hasEntry("cpu", new Quantity("100m")));
    assertThat(resources.getRequests(), hasEntry("memory", new Quantity("2Gi")));
    assertThat(resources.getRequests(), hasEntry("gpu", new Quantity("2")));
  }

  @Test
  public void overridesResourceRequestsWhenLoadedFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml")
        .withRequest(CPU.of("250m"));
    Pod pod = createPod(env);

    Container container = findHypeRunContainer(pod);
    ResourceRequirements resources = container.getResources();
    assertThat(resources.getRequests(), hasEntry("cpu", new Quantity("250m")));
  }

  @Test
  public void mountsSecretVolume() throws Exception {
    RunEnvironment env = get()
        .withSecret(SECRET);
    Pod pod = createPod(env);

    final PodSpec spec = pod.getSpec();
    assertThat(spec.getVolumes(), hasItems(new VolumeBuilder()
        .withName(SECRET.name())
        .withNewSecret()
            .withSecretName(SECRET.name())
        .endSecret()
        .build()));

    Container container = findHypeRunContainer(pod);
    assertThat(container.getVolumeMounts(), hasItems(new VolumeMountBuilder()
        .withName(SECRET.name())
        .withMountPath(SECRET.mountPath())
        .withReadOnly(true)
        .build()));
  }

  @Test
  public void mountsSecretVolumeWhenLoadedFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml")
        .withSecret(SECRET);
    Pod pod = createPod(env);

    final PodSpec spec = pod.getSpec();
    assertThat(spec.getVolumes(), hasItems(new VolumeBuilder()
        .withName(SECRET.name())
        .withNewSecret()
            .withSecretName(SECRET.name())
        .endSecret()
        .build()));

    Container container = findHypeRunContainer(pod);
    assertThat(container.getVolumeMounts(), hasItems(new VolumeMountBuilder()
        .withName(SECRET.name())
        .withMountPath(SECRET.mountPath())
        .withReadOnly(true)
        .build()));
  }

  @Test
  public void loadsFromYaml() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml");
    Pod pod = createPod(env);

    assertThat(pod.getSpec().getRestartPolicy(), is("Never"));

    Container container = findHypeRunContainer(pod);
    assertThat(container.getImage(), is("busybox:1"));
    assertThat(container.getImagePullPolicy(), is("Always"));
    assertThat(container.getEnv(), hasItems(envVar("EXAMPLE", "my-env-value")));

    ResourceRequirements resources = container.getResources();
    assertThat(resources.getRequests(), hasEntry("cpu", new Quantity("100m")));
    assertThat(resources.getLimits(), hasEntry("memory", new Quantity("1Gi")));
  }

  private Pod createPod(RunEnvironment env) {
    StagedContinuation cont = StagedContinuation.stagedContinuation(MANIFEST_PATH, MANIFEST);
    RunSpec runSpec = RunSpec.runSpec(env, cont, "busybox:1");

    return runner.createPod(runSpec);
  }

  private EnvVar envVar(String name, String value) {
    return new EnvVarBuilder()
        .withName(name)
        .withValue(value)
        .build();
  }

  private Container findHypeRunContainer(Pod pod) {
    try {
      return KubernetesDockerRunner.findHypeRunContainer(pod);
    } catch (Exception e) {
      throw new AssertionError(HYPE_RUN + " container missing in Pod spec", e);
    }
  }
}
