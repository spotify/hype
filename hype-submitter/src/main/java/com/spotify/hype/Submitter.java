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

import static java.util.stream.Collectors.toList;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.common.base.Throwables;
import com.spotify.hype.util.Fn;
import com.spotify.hype.util.SerializationUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: document.
 *
 * todo: hash file contents and dedupe uploads
 * todo: write explicit file list to gcs (allows for deduped & multi-use staging location)
 */
public class Submitter {

  private static final Logger LOG = LoggerFactory.getLogger(Submitter.class);

  private static final String GCS_STAGING_PREFIX = "spotify-hype-staging";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final ForkJoinPool FJP = new ForkJoinPool(32);

  private final Storage storage;
  private final String bucketName;
  private final ClasspathInspector classpathInspector;

  public static Submitter create(
      Storage storage, String bucketName, ClasspathInspector classpathInspector) {
    return new Submitter(storage, bucketName, classpathInspector);
  }

  private Submitter(Storage storage, String bucketName, ClasspathInspector classpathInspector) {
    this.storage = Objects.requireNonNull(storage);
    this.bucketName = Objects.requireNonNull(bucketName);
    this.classpathInspector = Objects.requireNonNull(classpathInspector);
  }

  public StagedContinuation stageContinuation(Fn<?> fn) {
    final List<Path> files = classpathInspector.localClasspathJars();
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

    return StagedContinuation.create(stageLocation, stagedFiles, continuationFileName);
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
}
