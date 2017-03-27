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
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.Image;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a short term implementation of DockerRunner that keeps track of started containers,
 * periodically polls them for exit status and triggers events when a container is found to
 * have exited.
 */
class LocalDockerRunner implements DockerRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LocalDockerRunner.class);

  private static final int CHECK_INTERVAL = 5;

  private final DockerClient client;

  LocalDockerRunner() {
    LOG.info("creating a client");
    try {
      client = DefaultDockerClient.fromEnv().build();
    } catch (DockerCertificateException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Optional<URI> run(RunSpec runSpec) {
    final String imageTag = runSpec.imageName().contains(":")
        ? runSpec.imageName()
        : runSpec.imageName() + ":latest";


    final ContainerCreation creation;
    try {
      boolean found = false;
      for (Image image : client.listImages()) {
        found |= image.repoTags().contains(imageTag);
        if (found) {
          break;
        }
      }

      if (!found) {
        client.pull(imageTag, System.out::println); // blocking
      }

      final HostConfig hostConfig =
          HostConfig.builder()
              .appendBinds(Bind.from(runSpec.secret().name())
                               .to(runSpec.secret().mountPath() + "/key.json")
                               .readOnly(true)
                               .build())
          .build();

      final ContainerConfig containerConfig = ContainerConfig.builder()
          .image(imageTag)
          .cmd(ImmutableList.of(runSpec.stagingLocation(), runSpec.functionFile()))
          .hostConfig(hostConfig)
          .build();
      creation = client.createContainer(containerConfig);
      client.startContainer(creation.id());
      LOG.info("Started container with id " + creation.id());
      blockUntilComplete(creation.id());
    } catch (DockerException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    LOG.info("Container completed with id " + creation.id());
//    return creation.id();
    throw new UnsupportedOperationException();
  }

  private void blockUntilComplete(final String containerId) throws InterruptedException {
    LOG.debug("Checking running statuses");

    while (true) {
      final ContainerInfo containerInfo;
      try {
        containerInfo = client.inspectContainer(containerId);
      } catch (DockerException | InterruptedException e) {
        LOG.error("Error while reading status from docker", e);
        return;
      }

      if (!containerInfo.state().running()) {
        final int exitCode = containerInfo.state().exitCode(); // FIXME
        LOG.info("Container {} exited with code {}", containerId, exitCode);
        break;
      }

      Thread.sleep(TimeUnit.SECONDS.toMillis(CHECK_INTERVAL));
    }
  }

  @Override
  public void close() throws IOException {
    client.close();
  }
}
