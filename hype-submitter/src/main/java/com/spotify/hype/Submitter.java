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
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: document.
 */
public class Submitter {

  private static final Logger LOG = LoggerFactory.getLogger(Submitter.class);

  private static final String CONT_FILE = "continuation-";
  private static final String RET_FILE = "return-";
  private static final String SER = ".ser";

  private static final String GCS_STAGING_PREFIX = "spotify-hype-staging";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final ForkJoinPool FJP = new ForkJoinPool(16);

  private final Storage storage;
  private final String bucketName;

  public Submitter(Storage storage, String bucketName) {
    this.storage = Objects.requireNonNull(storage);
    this.bucketName = Objects.requireNonNull(bucketName);
  }

  public URI stageFiles(List<String> fileUris) {
    LOG.info("Staging {} files", fileUris.size());


    try {
      FJP.submit(
        () -> fileUris.parallelStream()
            .forEach(this::getStagedURI))
        .get();
    } catch (InterruptedException | ExecutionException e) {
      throw Throwables.propagate(e);
    }

    try {
      return new URI("gs", bucketName, "/" + GCS_STAGING_PREFIX, null);
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }

  public static Path serializeContinuation(Fn<?> continuation) {
    return serializeObject(continuation, CONT_FILE);
  }

  public static Path serializeReturnValue(Object value) {
    return serializeObject(value, RET_FILE);
  }

  public static Fn<?> readContinuation(Path continuationPath) {
    return (Fn<?>) readObject(continuationPath);
  }

  private static String encodePart(String part) {
    try {
      return URLEncoder.encode(part, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw Throwables.propagate(e);
    }
  }

  private static String decodePart(String part) {
    try {
      return URLDecoder.decode(part, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw Throwables.propagate(e);
    }
  }

  private String getStagedURI(String uri) {
    uri = Stream.of(uri.split("/")).map(Submitter::encodePart).collect(joining("/"));
    URI parsed = URI.create(uri);

    if (!isNullOrEmpty(parsed.getScheme()) && !"file".equals(parsed.getScheme())) {
      return uri;
    }

    // either a file URI or just a path, stage it to GCS
    String filePath = Stream.of(parsed.getPath().split("/"))
        .map(Submitter::decodePart).collect(joining("/"));
    File local = Paths.get(filePath).toFile();
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

  private static Path serializeObject(Object obj, String filePrefix) {
    try {
      final Path stateFilePath = Files.createTempFile(filePrefix, SER);
      final File file = stateFilePath.toFile();
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
        oos.writeObject(obj);
      }
      return stateFilePath;
    } catch (IOException e) {
      e.printStackTrace();
      throw Throwables.propagate(e);
    }
  }

  private static Object readObject(Path continuationPath) {
    File file = continuationPath.toFile();
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
      return ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
      throw Throwables.propagate(e);
    }
  }
}
