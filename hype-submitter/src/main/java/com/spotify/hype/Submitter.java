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

import static com.spotify.hype.ClasspathInspector.forLoader;
import static com.spotify.hype.runner.RunSpec.runSpec;
import static java.util.stream.Collectors.toList;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Throwables;
import com.spotify.hype.runner.DockerRunner;
import com.spotify.hype.runner.RunSpec;
import com.spotify.hype.util.Fn;
import com.spotify.hype.util.SerializationUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * todo: hash file contents and dedupe uploads
 * todo: write explicit file list to gcs (allows for deduped & multi-use staging location)
 *
 * todo: Resource requests (cpu, mem)
 *       https://kubernetes.io/docs/concepts/policy/resource-quotas/

 * todo: gcePersistentDisk mounting
 * todo: PD scheduling (create r/w mode, pass along to other jobs, use OpProvider?)
 *       https://kubernetes.io/docs/concepts/storage/volumes/#gcepersistentdisk
 */
public class Submitter {

  private static final Logger LOG = LoggerFactory.getLogger(Submitter.class);

  private static final String GCS_STAGING_PREFIX = "spotify-hype-staging";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final ForkJoinPool FJP = new ForkJoinPool(32);

  private final Storage storage;
  private final ClasspathInspector classpathInspector;
  private final String bucketName;
  private final ContainerEngineCluster cluster;

  public static Submitter create(String bucketName, ContainerEngineCluster cluster) {
    final ClasspathInspector classpathInspector = forLoader(Submitter.class.getClassLoader());
    final Storage storage = StorageOptions.getDefaultInstance().getService();
    return create(storage, classpathInspector, bucketName, cluster);
  }

  public static Submitter create(
      Storage storage, ClasspathInspector classpathInspector,
      String bucketName, ContainerEngineCluster cluster) {
    return new Submitter(storage, classpathInspector, bucketName, cluster);
  }

  private Submitter(
      Storage storage, ClasspathInspector classpathInspector, String bucketName,
      ContainerEngineCluster cluster) {
    this.storage = Objects.requireNonNull(storage);
    this.bucketName = Objects.requireNonNull(bucketName);
    this.classpathInspector = Objects.requireNonNull(classpathInspector);
    this.cluster = Objects.requireNonNull(cluster);
  }

  public <T> T runOnCluster(Fn<T> fn, RunEnvironment environment) {
    // 1. stage
    final StagedContinuation stagedContinuation = stageContinuation(fn);

    // 2. submit and wait for k8s pod (returns return value uri, termination log, etc)
    final RunSpec runSpec = runSpec(environment, stagedContinuation);

    try (KubernetesClient kubernetesClient = DockerRunner.createKubernetesClient(cluster)) {
      final DockerRunner kubernetes = DockerRunner.kubernetes(kubernetesClient);
      final Optional<URI> returnUri = kubernetes.run(runSpec);

      // 3. download serialized return value
      if (returnUri.isPresent()) {
        final Path path = downloadFile(returnUri.get());

        // 4. deserialize and return
        //noinspection unchecked
        return (T) SerializationUtil.readObject(path);
      } else {
        throw new RuntimeException("Failed to get return value");
      }
    }
  }

  public StagedContinuation stageContinuation(Fn<?> fn) {
    final List<Path> files = classpathInspector.classpathJars();
    final Path continuationPath = SerializationUtil.serializeContinuation(fn);
    final String continuationFileName = continuationPath.getFileName().toString();
    files.add(continuationPath.toAbsolutePath());

    final Path prefix = Paths.get(GCS_STAGING_PREFIX /* ,todo prefix */);
    final List<URI> stagedFiles = stageFiles(files, prefix);
    final URI stageLocation;
    try {
      stageLocation = new URI("gs", bucketName, "/" + prefix.toString(), null);
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }

    return StagedContinuation.stagedContinuation(stageLocation, stagedFiles, continuationFileName);
  }

  public List<URI> stageFiles(List<Path> files, Path prefix) {
    LOG.info("Staging {} files", files.size());

    try {
      return FJP.submit(
        () -> files.parallelStream()
            .map(file -> upload(prefix, file))
            .collect(toList()))
        .get();
    } catch (InterruptedException | ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  private URI upload(Path prefix, Path file) {
    LOG.debug("Staging {} in GCS bucket {}", file, bucketName);
    return upload(file.toFile(), storage, bucketName, prefix);
  }

  private static URI upload(File file, Storage storage, String bucketName, Path prefix) {
    try (FileInputStream inputStream = new FileInputStream(file)) {
      Bucket bucket = storage.get(bucketName);
      String blobName = prefix.resolve(file.getName()).toString();
      Blob blob = bucket.create(blobName, inputStream, APPLICATION_OCTET_STREAM);

      return new URI("gs", blob.getBucket(), "/" + blob.getName(), null);
    } catch (URISyntaxException | IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private Path downloadFile(URI uri) {
    final String bucket =  uri.getAuthority();
    final String blobId = uri.getPath().substring(1); // remove leading '/'
    final Blob blob = storage.get(bucket, blobId);

    final Path localFilePath;
    try {
      final Path temp = Files.createTempDirectory("hype-submit");
      final String localFileName = Paths.get(blob.getName()).getFileName().toString();
      localFilePath = temp.resolve(localFileName);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    try (OutputStream bos = new BufferedOutputStream(new FileOutputStream(localFilePath.toFile()))) {
      try (ReadableByteChannel reader = blob.reader()) {
        final WritableByteChannel writer = Channels.newChannel(bos);
        final ByteBuffer buffer = ByteBuffer.allocate(8192);

        int read;
        while ((read = reader.read(buffer)) > 0) {
          buffer.rewind();
          buffer.limit(read);

          while (read > 0) {
            read -= writer.write(buffer);
          }

          buffer.clear();
        }
      }
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    return localFilePath;
  }
}
