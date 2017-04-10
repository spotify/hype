package com.spotify.hype.runner;

import static com.google.common.collect.ImmutableList.of;
import static com.spotify.hype.runner.KubernetesDockerRunner.HYPE_RUN;
import static com.spotify.hype.runner.KubernetesDockerRunner.POLL_PODS_INTERVAL_SECONDS;
import static com.spotify.hype.util.Util.randomAlphaNumeric;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.Secret;
import com.spotify.hype.model.StagedContinuation;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by robertg on 4/10/17.
 */
public class LocalDockerRunner implements DockerRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LocalDockerRunner.class);
  private static final String GCLOUD_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

  private final DockerClient client;

  public LocalDockerRunner(final DockerClient client) {
    this.client = client;
  }

  @Override
  public Optional<URI> run(final RunSpec runSpec) {
    final String runName = HYPE_RUN + "-" + randomAlphaNumeric(8);
    final RunEnvironment env = runSpec.runEnvironment();
    final Secret secret = env.secretMount();
    final StagedContinuation stagedContinuation = runSpec.stagedContinuation();

    final String imageWithTag = env.image().contains(":")
                                ? env.image()
                                : env.image() + ":latest";

    final ContainerCreation creation;
    try {
      boolean found = false;
      for (Image image : client.listImages()) {
        found = image.repoTags().contains(imageWithTag);
        if (found) {
          break;
        }
      }

      if (!found) {
        client.pull(imageWithTag, System.out::println); // blocking
      }

      final HostConfig.Builder hostConfig = HostConfig.builder();
      // Use GOOGLE_APPLICATION_CREDENTIALS environment variable to mount into
      final String credentials = System.getenv(GCLOUD_CREDENTIALS);
      if (credentials == null) {
        throw new RuntimeException(
            String.format("Environment variable %s must be set to use local runner",
                          GCLOUD_CREDENTIALS)
        );
      }

      hostConfig.appendBinds(HostConfig.Bind.from(credentials)
                               .to("/etc/gcloud/key.json")
                               .readOnly(true)
                               .build());

      // Mount temporary file to act as the termination log
      final Path terminationLog = Files.createTempFile("termination-log", ".txt");
      hostConfig.appendBinds(HostConfig.Bind.from(terminationLog.toString())
                                 .to("/dev/termination")
                                 .readOnly(false)
                                 .build());


      final ContainerConfig containerConfig = ContainerConfig.builder()
          .image(imageWithTag)
          .cmd(of(stagedContinuation.manifestPath().toUri().toString()))
          .hostConfig(hostConfig.build())
          .build();
      creation = client.createContainer(containerConfig);
      client.startContainer(creation.id());
      LOG.info("Started container {}", creation.id());
      final Optional<URI> uri = blockUntilComplete(creation.id(), terminationLog);
      client.removeContainer(creation.id());
      return uri;
    } catch (DockerException | IOException e) {
      throw new RuntimeException("Failed to start docker container", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while blocking", e);
    }
  }

  private Optional<URI> blockUntilComplete(final String containerId,
                                           final Path terminationLog) throws InterruptedException {
    LOG.debug("Checking running statuses");

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

      Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_PODS_INTERVAL_SECONDS));
    }
  }
}
