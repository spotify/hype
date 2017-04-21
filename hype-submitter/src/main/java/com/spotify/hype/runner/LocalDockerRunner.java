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
import com.spotify.docker.client.messages.Image;
import com.spotify.hype.model.LoggingSidecar;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.StagedContinuation;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDockerRunner implements DockerRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LocalDockerRunner.class);
  private static final String GCLOUD_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
  private static final String STAGING_VOLUME = "/staging";
  private static final String GCLOUD_CREDENTIALS_MOUNT = "/etc/gcloud/key.json";
  private static final int POLL_CONTAINERS_INTERVAL_SECONDS = 1;
  static final String HYPE_LOGGING_VOLUME = "/hype-logging";
  static final String HYPE_LOGGING_VOLUME_ENV = "HYPE_LOGGING_VOLUME";
  private static final int WAIT_BEFORE_KILLING_CONTAINER_SECONDS = 60;

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

  private void fetchImage(String imageWithTag) throws DockerException, InterruptedException {
    // check if it's needed to pull the image
    // TODO: figure out authentication with private repos
    final List<Image> images = client.listImages();
    if (images.stream().noneMatch(i ->
                                      i.repoTags() != null
                                      && i.repoTags().stream().anyMatch(t -> Objects.equals(t, imageWithTag)))) {
      LOG.info("Pulling image " + imageWithTag);
      try {
        client.pull(imageWithTag, System.out::println); // blocking
      } catch (DockerException e) {
        LOG.error("Could not pull the image " + imageWithTag + ". Try to pull it yourself.");
        throw e;
      }
    }
  }

  @Override
  public Optional<URI> run(final RunSpec runSpec) {
    final RunEnvironment env = runSpec.runEnvironment();
    final StagedContinuation stagedContinuation = runSpec.stagedContinuation();
    final String imageWithTag = runSpec.image();
    final boolean hypeLoggingEnabled = runSpec.loggingSidecar() != null;

    final ContainerCreation creation;
    try {
      fetchImage(imageWithTag);

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
      final Path termLogs =
          Files.createDirectories(localTmp.resolve("spotify-hype-termination-logs"));
      final Path terminationLog = Files.createTempFile(termLogs, "termination-log", ".txt");
      if (!keepTerminationLog) {
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
      env.volumeMounts().forEach(m -> {
        String localVolume = volumes.resolve(m.volumeRequest().id()).toString();
        hostConfig.appendBinds(HostConfig.Bind
            .from(localVolume)
            .to(m.mountPath())
            .build());
      });

      final File stagingContinuationFile = stagedContinuation.manifestPath().toFile();

      final ContainerConfig.Builder containerBuilder = ContainerConfig.builder()
          .image(imageWithTag)
          .cmd(of("file://" + STAGING_VOLUME + "/" + stagingContinuationFile.getName()))
          .hostConfig(hostConfig.build());

      if (hypeLoggingEnabled) {
        containerBuilder
            .env(HYPE_LOGGING_VOLUME_ENV + "=" + HYPE_LOGGING_VOLUME)
            .addVolume(HYPE_LOGGING_VOLUME);
      }

      final ContainerConfig containerConfig = containerBuilder.build();
      creation = client.createContainer(containerConfig);

      final Optional<ContainerCreation> loggingContainer;
      if (hypeLoggingEnabled) {
        loggingContainer = Optional.of(startLoggingContainer(creation, runSpec.loggingSidecar()));
      } else {
        loggingContainer = Optional.empty();
      }

      client.startContainer(creation.id());
      LOG.info("Started container {}", creation.id());

      final Optional<URI> uri = blockUntilComplete(creation.id(), terminationLog);

      cleanupContainer(creation);

      if (loggingContainer.isPresent()) {
        final ContainerCreation loggingContainerCreation = loggingContainer.get();
        client.stopContainer(loggingContainerCreation.id(), WAIT_BEFORE_KILLING_CONTAINER_SECONDS);
        cleanupContainer(loggingContainerCreation);
      }

      return uri.map(u -> stagingContinuationFile.toPath()
          .resolveSibling(Paths.get(u).toFile().getName()).toUri());
    } catch (DockerException | IOException e) {
      throw new RuntimeException("Failed to start docker container", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while blocking", e);
    }
  }

  private void cleanupContainer(ContainerCreation creation)
      throws DockerException, InterruptedException {
    if (!keepContainer) {
      if (Objects.equals(System.getenv("CIRCLECI"), "true")) {
        LOG.info("Running on CircleCi - won't delete container due to " +
                 " https://circleci.com/docs/1.0/docker-btrfs-error/");
      } else {
        client.removeContainer(creation.id());
      }
    }
  }

  private ContainerCreation startLoggingContainer(final ContainerCreation mainContainer,
                                                  final LoggingSidecar loggingSidecar) {
    try {
      final String loggingImage = loggingSidecar.image();
      fetchImage(loggingImage);

      final HostConfig.Builder hostConfig = HostConfig.builder();

      hostConfig.volumesFrom(mainContainer.id());

      final ContainerConfig containerConfig = ContainerConfig.builder()
          .image(loggingImage)
          .cmd(loggingSidecar.args())
          .hostConfig(hostConfig.build())
          .env(HYPE_LOGGING_VOLUME_ENV + "=" + HYPE_LOGGING_VOLUME)
          .build();

      final ContainerCreation loggingContainer = client.createContainer(containerConfig);
      client.startContainer(loggingContainer.id());
      LOG.info("Started logging sidecar container {}", loggingContainer.id());

      return loggingContainer;
    } catch (DockerException e) {
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
