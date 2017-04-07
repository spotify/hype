/*-
 * -\-\-
 * hype-gcs
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

package com.spotify.hype.gcs;

import static com.google.common.collect.Iterables.concat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public final class ManifestLoader {

  private static final ForkJoinPool FJP = new ForkJoinPool(32);

  public static RunManifest downloadManifest(Path manifestPath, Path destinationDir) throws IOException {
    final RunManifest manifest = ManifestUtil.read(manifestPath);

    final Set<Path> manifestEntries = new LinkedHashSet<>();
    manifestEntries.add(manifestPath.resolveSibling(manifest.continuation()));
    for (String file : concat(manifest.classPathFiles(), manifest.files())) {
      manifestEntries.add(manifestPath.resolveSibling(file));
    }

    try {
      FJP.submit(() -> manifestEntries.parallelStream()
          .forEach(filePath -> downloadFile(filePath, destinationDir)))
      .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    return manifest;
  }

  private static void downloadFile(Path filePath, Path destinationDir) {
    final Path destinationFile = destinationDir.resolve(filePath.getFileName().toString());
    try {
      // todo: retries
      Files.copy(filePath, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
