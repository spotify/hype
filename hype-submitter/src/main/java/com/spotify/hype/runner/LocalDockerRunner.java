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

import static com.google.common.collect.ImmutableList.of;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.StagedContinuation;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDockerRunner implements DockerRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LocalDockerRunner.class);
  private static final String GCLOUD_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
  private static final String STAGING_VOLUME = "/staging";
  private static final String GCLOUD_CREDENTIALS_MOUNT = "/etc/gcloud/key.json";
  private static final int POLL_CONTAINERS_INTERVAL_SECONDS = 5;

  private final DockerClient client;
  private final Boolean keepContainer;
  private final Boolean keepTerminationLog;
  private final Boolean keepVolumes;

  public LocalDockerRunner(final DockerClient client,
                           final Boolean keepContainer,
                           final Boolean keepTerminationLog,
                           final Boolean keepVolumes) {
    this.client = client;
    this.keepContainer = keepContainer;
    this.keepTerminationLog = keepTerminationLog;
    this.keepVolumes = keepVolumes;
  }

  private String getImageWithTag(final RunSpec runSpec) {
    final RunEnvironment.EnvironmentBase base = runSpec.runEnvironment().base();
    if (base instanceof RunEnvironment.SimpleBase) {
      final RunEnvironment.SimpleBase simpleBase = (RunEnvironment.SimpleBase) base;
      return simpleBase.image().contains(":")
          ? simpleBase.image()
          : simpleBase.image() + ":latest";
    } else if (base instanceof RunEnvironment.YamlBase) {
      throw
          new UnsupportedOperationException(
              "Yaml based environment not supported in local mode yet!");
    }
    throw new IllegalArgumentException("Illegal type of run environment " + base.toString());
  }

  @Override
  public Optional<URI> run(final RunSpec runSpec) {
    final RunEnvironment env = runSpec.runEnvironment();
    final StagedContinuation stagedContinuation = runSpec.stagedContinuation();
    final String imageWithTag = getImageWithTag(runSpec);

    final ContainerCreation creation;
    try {
      client.pull(imageWithTag, System.out::println); // blocking
      final HostConfig.Builder hostConfig = HostConfig.builder();
      // Use GOOGLE_APPLICATION_CREDENTIALS environment variable to mount into
      final String credentials = System.getenv(GCLOUD_CREDENTIALS);
      if (credentials == null) {
        LOG.warn(GCLOUD_CREDENTIALS + " not set, won't mount gcloud credentials");
      } else {
        LOG.info("Mounting `" + credentials + "` as `" + GCLOUD_CREDENTIALS_MOUNT + "`");
        hostConfig.appendBinds(HostConfig.Bind.from(credentials)
            .to(GCLOUD_CREDENTIALS_MOUNT)
            .readOnly(true)
            .build());
      }

      // Mount temporary file to act as the termination log
      // Use user home because Docker Engine daemon has only limited access to on macOS or
      // Windows filesystem
      final Path localTmp = new File(System.getProperty("user.home")).toPath().resolve(".tmp");
      final Path termLogs = Files.createDirectories(localTmp.resolve("spotify-hype-termination-logs"));
      final Path terminationLog = Files.createTempFile(termLogs, "termination-log", ".txt");
      if(!keepTerminationLog) {
        terminationLog.toFile().deleteOnExit();
      }
      hostConfig.appendBinds(HostConfig.Bind
          .from(terminationLog.toString())
          .to("/dev/termination-log")
          .readOnly(false)
          .build());

      hostConfig.appendBinds(HostConfig.Bind
          .from(runSpec.stagedContinuation().manifestPath().getParent().toString())
          .to(STAGING_VOLUME)
          .readOnly(false)
          .build());

      Path volumes = Files.createDirectories(localTmp.resolve("spotify-hype-volumes"));
      runSpec.runEnvironment().volumeMounts().forEach(m -> {
        String localVolume = volumes.resolve(m.volumeRequest().id()).toString();
        hostConfig.appendBinds(HostConfig.Bind
            .from(localVolume)
            .to(m.mountPath())
            .build());
      });

      final File stagingContinuationFile = stagedContinuation.manifestPath().toFile();

      final ContainerConfig containerConfig = ContainerConfig.builder()
          .image(imageWithTag)
          .cmd(of("file://" + STAGING_VOLUME + "/" + stagingContinuationFile.getName()))
          .hostConfig(hostConfig.build())
          .build();
      creation = client.createContainer(containerConfig);
      client.startContainer(creation.id());
      LOG.info("Started container {}", creation.id());
      final Optional<URI> uri = blockUntilComplete(creation.id(), terminationLog);
      if(!keepContainer) {
        client.removeContainer(creation.id());
      }
      return uri.map(u -> stagingContinuationFile.toPath()
          .resolveSibling(Paths.get(u).toFile().getName()).toUri());
    } catch (DockerException | IOException e) {
      throw new RuntimeException("Failed to start docker container", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while blocking", e);
    }
  }

  private Optional<URI> blockUntilComplete(final String containerId,
                                           final Path terminationLog) throws InterruptedException {
    while (true) {
      final ContainerInfo containerInfo;
      try {
        containerInfo = client.inspectContainer(containerId);

        if (!containerInfo.state().running()) {
          final int exitCode = containerInfo.state().exitCode();
          LOG.info("Docker container {} exited with exit code {}", containerId, exitCode);

          if (exitCode == 0) {
            final File terminationFile = terminationLog.toFile();
            if (terminationFile.exists()) {
              final String message = new String(Files.readAllBytes(terminationLog));
              LOG.info("Got termination message: {}", message);
              return Optional.of(URI.create(message));
            }
          }
          return Optional.empty();
        }
      } catch (DockerException | InterruptedException | IOException e) {
        LOG.error("Error while reading status from docker", e);
        return Optional.empty();
      }

      Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_CONTAINERS_INTERVAL_SECONDS));
    }
  }
}
