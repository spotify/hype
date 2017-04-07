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

package com.spotify.hype;

import static com.google.common.io.Files.getNameWithoutExtension;
import static com.spotify.hype.ClasspathInspector.forLoader;
import static com.spotify.hype.model.StagedContinuation.stagedContinuation;
import static com.spotify.hype.runner.RunSpec.runSpec;
import static java.nio.file.Files.newInputStream;
import static java.util.stream.Collectors.toList;

import com.spotify.hype.gcs.StagingUtil;
import com.spotify.hype.gcs.StagingUtil.StagedPackage;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.StagedContinuation;
import com.spotify.hype.runner.DockerRunner;
import com.spotify.hype.runner.RunSpec;
import com.spotify.hype.runner.VolumeRepository;
import com.spotify.hype.util.Fn;
import com.spotify.hype.util.SerializationUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * todo: write explicit file list to gcs (allows for deduped and multi-use staging location)
 */
public class Submitter implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(Submitter.class);

  private static final String GCS_STAGING_PREFIX = "spotify-hype-staging";

  private final ClasspathInspector classpathInspector;
  private final String bucketName;

  private final VolumeRepository volumeRepository;
  private final DockerRunner runner;

  public static Submitter create(String bucketName, ContainerEngineCluster cluster) {
    final ClasspathInspector classpathInspector = forLoader(Submitter.class.getClassLoader());
    return create(classpathInspector, bucketName, cluster);
  }

  public static Submitter create(
      ClasspathInspector classpathInspector,
      String bucketName, ContainerEngineCluster cluster) {
    return new Submitter(classpathInspector, bucketName, cluster);
  }

  private Submitter(
      ClasspathInspector classpathInspector, String bucketName,
      ContainerEngineCluster cluster) {
    this.bucketName = Objects.requireNonNull(bucketName);
    this.classpathInspector = Objects.requireNonNull(classpathInspector);

    final KubernetesClient client = getClient(cluster);
    this.volumeRepository = new VolumeRepository(client);
    this.runner = DockerRunner.kubernetes(client, volumeRepository);
  }

  public <T> T runOnCluster(Fn<T> fn, RunEnvironment environment) {
    // 1. stage
    final StagedContinuation stagedContinuation = stageContinuation(fn);

    // 2. submit and wait for k8s pod (returns return value uri, termination log, etc)
    final RunSpec runSpec = runSpec(environment, stagedContinuation);

    LOG.info("Submitting to {}", environment);
    final Optional<URI> returnUri = runner.run(runSpec);

    // 3. download serialized return value
    if (returnUri.isPresent()) {
      final Path path = Paths.get(returnUri.get());
      final T returnValue;
      try (InputStream inputStream = newInputStream(path)) {
        // 4. deserialize and return
        //noinspection unchecked
        returnValue = (T) SerializationUtil.readObject(inputStream);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      waitForDetach(environment);

      return returnValue;
    } else {
      throw new RuntimeException("Failed to get return value");
    }
  }

  public StagedContinuation stageContinuation(Fn<?> fn) {
    final List<Path> files = classpathInspector.classpathJars();
    final Path continuationPath = SerializationUtil.serializeContinuation(fn);
    final URI stagingLocation = Paths.get(URI.create("gs://" + bucketName))
        .resolve(GCS_STAGING_PREFIX)
        .toUri();
    final String continuationFileName = getNameWithoutExtension(continuationPath
        .toAbsolutePath().toString());

    files.add(continuationPath.toAbsolutePath());

    final List<String> fileStrings = files.stream()
        .map(Path::toAbsolutePath)
        .map(Path::toString)
        .collect(toList());

    final List<StagedPackage> stagedPackages = StagingUtil.stageClasspathElements(
        fileStrings,
        stagingLocation.toString());

    final Optional<StagedPackage> stagedContinuationPackage = stagedPackages.stream()
        .filter(p -> p.getName().contains(continuationFileName))
        .findFirst();

    if (!stagedContinuationPackage.isPresent()) {
      throw new RuntimeException();
    }

    final List<URI> stagedFiles = stagedPackages.stream()
        .map(StagedPackage::getLocation)
        .map(URI::create)
        .collect(toList());

    final URI uri = URI.create(stagedContinuationPackage.get().getLocation());
    final String cont = new File(uri.getPath()).getName();

    return stagedContinuation(stagingLocation, stagedFiles, cont);
  }

  @Override
  public void close() throws IOException {
    if (volumeRepository != null) {
      volumeRepository.close();
    }
  }

  /**
   * Hacky workaround for allowing k8s to detach any ReadWrite volumes from the nodes before
   * we continue to submit more pods.
   *
   * <p>A volume can be used in ReadOnly mode even when it is attached to a node in ReadWrite
   * mode. However, it can only be attached in ReadWrite mode to a single node.
   *
   * <p>If several pods requesting the same volume in ReadOnly mode are submitted too quickly
   * after it has been used in ReadWrite mode, they'll be able to use it immediately on the node
   * that has already has it attached as ReadWrite. All other nodes will have to wait for the
   * pods on that node to complete, and the node to detach the volume, before they are able to
   * attach the volume in ReadOnly mode. This leads to unnecessary contention between nodes and
   * reduces node-parallelism of submitted pods down to one node.
   */
  private void waitForDetach(RunEnvironment environment) {
    if (environment.volumeMounts().stream().anyMatch(v -> !v.readOnly())) {
      try {
        Thread.sleep(10_000);
      } catch (InterruptedException ignore) {
      }
    }
  }

  private static KubernetesClient client;
  private static synchronized KubernetesClient getClient(ContainerEngineCluster cluster) {
    if (client == null) {
      client = DockerRunner.createKubernetesClient(cluster);
    }

    return client;
  }
}
