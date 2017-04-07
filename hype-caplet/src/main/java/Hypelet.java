/*-
 * -\-\-
 * hype-run
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

import static com.google.cloud.storage.contrib.nio.CloudStorageOptions.withMimeType;
import static com.google.common.base.Charsets.UTF_8;
import static com.spotify.hype.util.Util.randomAlphaNumeric;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.spotify.hype.gcs.ManifestLoader;
import com.spotify.hype.gcs.RunManifest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Capsule caplet that downloads a Google Cloud Storage (gs://) run-manifest to a temp directory
 * and adds the included files to the application JVM classpath.
 *
 * <p>It takes one command line arguments: {@code <run-manifest-gcs-uri>}.
 *
 * <p>After staging the manifest contents locally, it invokes the inner JVM by replacing the first
 * argument with a path to the local temp directory. It also adds two additional arguments to the
 * application JVM, pointing to 1. the continuation file, and 2. to an output file which can be
 * written to. If the output file is written, it will be uploaded to the GCS staging uri when the
 * application JVM exits.
 */
public class Hypelet extends Capsule {

  private static final String NOOP_MODE = "noop";
  private static final String STAGING_PREFIX = "hype-run-";
  private static final String BINARY = "application/octet-stream";
  private static final String TERMINATION_LOG = "/dev/termination-log";
  private static final String HYPE_EXECUTION_ID = "HYPE_EXECUTION_ID";

  private final List<Path> downloadedJars = new ArrayList<>();

  private Path manifestPath;
  private Path stagingDir;
  private String returnFile;

  public Hypelet(Capsule pred) {
    super(pred);
  }

  @Override
  protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
    if (NOOP_MODE.equals(getMode())) {
      return super.prelaunch(jvmArgs, args);
    }

    if (args.size() < 2) {
      throw new IllegalArgumentException("Usage: <gcs-staging-uri> <continuation-file>");
    }

    System.out.println("=== HYPE RUN CAPSULE (v" + getVersion() + ") ===");

    try {
      manifestPath = Paths.get(URI.create(args.get(0)));
      stagingDir = Files.createTempDirectory(STAGING_PREFIX);

      System.out.println("Downloading files from " + manifestPath);
      // print manifest
      Files.copy(manifestPath, System.out);
      final RunManifest manifest = ManifestLoader.downloadManifest(manifestPath, stagingDir);
      System.out.println("Done downloading");

      manifest.classPathFiles().stream()
          .map(classPathFile -> stagingDir.resolve(classPathFile))
          .forEach(downloadedJars::add);

      returnFile = manifest.continuation()
          .replaceFirst("\\.bin", "-" + getRunId() + "-return.bin");

      // inner jvm args [tmpDir, continuation-filename, output-filename]
      final List<String> stubArgs = new ArrayList<>(args.size());
      stubArgs.add(stagingDir.toString());
      stubArgs.add(manifest.continuation());
      stubArgs.add(returnFile);
      return super.prelaunch(jvmArgs, stubArgs);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void cleanup() {
    if (stagingDir != null) {
      final Path returnFilePath = stagingDir.resolve(returnFile);
      if (Files.exists(returnFilePath)) {
        try {
          System.out.println("Uploading serialized return value: " + returnFilePath);
          final Path uploadPath = manifestPath.resolveSibling(returnFile);
          Files.copy(returnFilePath, uploadPath, withMimeType(BINARY));
          System.out.println("Uploaded to: " + uploadPath.toUri());

          // write the uploaded uri to the termination log if it exists
          final Path terminationLog = Paths.get(TERMINATION_LOG);
          if (Files.exists(terminationLog)) {
            final byte[] terminationMessage = uploadPath.toUri().toString().getBytes(UTF_8);
            try {
              Files.write(terminationLog, terminationMessage, CREATE, WRITE, TRUNCATE_EXISTING);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    super.cleanup();
  }

  @Override
  protected Object lookup0(Object x, String type,
                           Map.Entry<String, ?> attrContext,
                           Object context) {
    final Object o = super.lookup0(x, type, attrContext, context);
    if ("App-Class-Path".equals(attrContext.getKey())) {
      final List<Path> lookup = new ArrayList<>((List<Path>) o);
      lookup.addAll(downloadedJars);
      return lookup;
    }
    return o;
  }

  private static String getVersion() {
    Properties props = new Properties();
    try {
      props.load(Hypelet.class.getResourceAsStream("/version.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return props.getProperty("hypelet.version");
  }

  private static String getRunId() {
    return System.getenv().containsKey(HYPE_EXECUTION_ID)
        ? System.getenv(HYPE_EXECUTION_ID)
        : randomAlphaNumeric(8);
  }
}
