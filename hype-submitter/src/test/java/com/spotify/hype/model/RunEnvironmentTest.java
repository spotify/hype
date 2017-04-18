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

package com.spotify.hype.model;

import static com.github.npathai.hamcrestopt.OptionalMatchers.hasValue;
import static com.spotify.hype.model.ResourceRequest.CPU;
import static com.spotify.hype.model.ResourceRequest.MEMORY;
import static com.spotify.hype.model.RunEnvironment.environment;
import static com.spotify.hype.model.RunEnvironment.fromYaml;
import static com.spotify.hype.model.Secret.secret;
import static com.spotify.hype.model.VolumeRequest.volumeRequest;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertThat;

import com.spotify.hype.model.RunEnvironment.SimpleBase;
import com.spotify.hype.model.RunEnvironment.YamlBase;
import org.junit.Test;

public class RunEnvironmentTest {

  private static final VolumeRequest VOLUME1 = volumeRequest("test", "10Gi");
  private static final VolumeRequest VOLUME2 = volumeRequest("test", "10Gi");

  @Test
  public void overrideResourceRequestsForSame() throws Exception {
    RunEnvironment env = environment("image")
        .withRequest(CPU.of("1"))
        .withRequest(CPU.of("7"));

    assertThat(env.resourceRequests().size(), equalTo(1));
    assertThat(env.resourceRequests(), hasEntry("cpu", "7"));
  }

  @Test
  public void overrideSimpleBaseImage() throws Exception {
    RunEnvironment env = environment("image")
        .withImageOverride("override-image");

    assertThat(((SimpleBase) env.base()).image(), equalTo("override-image"));
  }

  @Test
  public void overrideYamlBaseImage() throws Exception {
    RunEnvironment env = fromYaml("/minimal-pod.yaml")
        .withImageOverride("override-image");

    assertThat(((YamlBase) env.base()).overrideImage(), hasValue("override-image"));
  }

  @Test
  public void retainConfigurationOnImageOverride_Simple() throws Exception {
    RunEnvironment env = configure(environment("image"))
        .withImageOverride("override-image");

    assertConfigured(env);
  }

  @Test
  public void retainConfigurationOnImageOverride_Yaml() throws Exception {
    RunEnvironment env = configure(fromYaml("/minimal-pod.yaml"))
        .withImageOverride("override-image");

    assertConfigured(env);
  }

  private static RunEnvironment configure(RunEnvironment environment) {
    return environment
        .withRequest(CPU.of("1"))
        .withRequest(MEMORY.of("4Gi"))
        .withMount(VOLUME1.mountReadWrite("/test1"))
        .withMount(VOLUME2.mountReadOnly("/test2"))
        .withSecret("secret", "/secret");
  }

  private static void assertConfigured(RunEnvironment environment) {
    assertThat(environment.secretMounts(), contains(secret("secret", "/secret")));
    assertThat(environment.resourceRequests(), hasEntry("cpu", "1"));
    assertThat(environment.resourceRequests(), hasEntry("memory", "4Gi"));
    assertThat(environment.volumeMounts(), contains(
        VOLUME1.mountReadWrite("/test1"),
        VOLUME2.mountReadOnly("/test2")));
  }
}
