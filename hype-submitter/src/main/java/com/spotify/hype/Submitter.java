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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: document.
 */
public class Submitter {

  private static final Logger LOG = LoggerFactory.getLogger(Submitter.class);

  private static final String GCS_STAGING_PREFIX = "spotify-hype-staging"; // ?
  private static final ForkJoinPool FJP = new ForkJoinPool(16);
  public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

  private final Storage storage;
  private final String bucketName;

  public Submitter(Storage storage, String bucketName) {
    this.storage = Objects.requireNonNull(storage);
    this.bucketName = Objects.requireNonNull(bucketName);
  }

  public List<String> stage(List<String> fileUris) {
    final List<String> stagedFileUris;
    try {
      stagedFileUris = FJP.submit(
          () -> fileUris.parallelStream()
              .map(this::getStagedURI)
              .collect(toList()))
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw Throwables.propagate(e);
    }

    return stagedFileUris;
  }

  private String getStagedURI(String uri) {
    URI parsed = URI.create(uri);

    if (!isNullOrEmpty(parsed.getScheme()) && !"file".equals(parsed.getScheme())) {
      return uri;
    }

    // either a file URI or just a path, stage it to GCS
    File local = Paths.get(uri).toFile();
    LOG.debug("Staging {} in GCS bucket {}", uri, bucketName);

    try {
      Bucket bucket = storage.get(bucketName);
      String blobName = Paths.get(GCS_STAGING_PREFIX, local.getName()).toString();
      FileInputStream inputStream = new FileInputStream(local);
      Blob blob = bucket.create(blobName, inputStream, APPLICATION_OCTET_STREAM);

      return new URI("gs", blob.getBucket(), "/" + blob.getName(), null).toString();
    } catch (URISyntaxException | IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
