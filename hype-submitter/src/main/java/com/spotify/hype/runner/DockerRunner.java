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

import com.google.auto.value.AutoValue;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.Closeable;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines an interface to the Docker execution environment
 */
public interface DockerRunner extends Closeable {

  Logger LOG = LoggerFactory.getLogger(DockerRunner.class);

  /**
   * Runs a hype execution. Blocks until complete.
   *
   * @param runSpec     Specification of what to run
   * @return The execution id for the docker instance which ran.
   */
  String run(RunSpec runSpec) throws IOException;

  @AutoValue
  abstract class RunSpec {

    public abstract String imageName();

    public abstract String stagingLocation();

    public abstract String functionFile();

    public abstract String jsonKeyPath();

    public static RunSpec create(
        String imageName,
        String stagingLocation,
        String functionFile,
        String jsonKeyPath
    ) {
      return new AutoValue_DockerRunner_RunSpec(imageName, stagingLocation, functionFile, jsonKeyPath);
    }
  }

  /**
   * A local runner
   *
   * @return A locally operating docker runner
   */
  static DockerRunner local() {
    return new LocalDockerRunner();
  }

  static DockerRunner kubernetes(KubernetesClient kubernetesClient) {
    return new KubernetesDockerRunner(kubernetesClient);
  }

  public static void main(String[] args) throws IOException {
    // Need to inject environment variables such as
    final DockerRunner kubernetes = DockerRunner.kubernetes(new DefaultKubernetesClient());
    final DockerRunner local = DockerRunner.local();

    final RunSpec runSpec =
        RunSpec.create("hype-runner", "gs://user-track-plays/spotify-hype-staging",
                       "continuation-4792440913169484884.ser", "/home/robertg/installs/discover-weekly.json");
    local.run(runSpec);
  }
}
