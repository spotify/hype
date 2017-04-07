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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ManifestUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ManifestUtil.class);

  private static final char LAMBDA = 'l';
  private static final char CLASSPATH_FILE = 'c';
  private static final char REGULAR_FILE = 'f';

  static RunManifest read(Path manifestPath) throws IOException {
    final Stream<String> lines = Files.lines(manifestPath);

    final RunManifestBuilder builder = new RunManifestBuilder();
    lines.forEachOrdered(line -> {
      final String[] split = line.trim().split(" ", 2);
      if (split.length != 2) {
        throw new IllegalArgumentException("Malformed manifest line '" + line + "'");
      }

      switch (split[0].charAt(0)) {
        case LAMBDA:
          builder.continuation(split[1]);
          break;

        case CLASSPATH_FILE:
          builder.addClassPathFile(split[1]);
          break;

        case REGULAR_FILE:
          builder.addFile(split[1]);
          break;

        default:
          LOG.warn("Unrecognized manifest entry '" + line + "'");
      }
    });

    return builder.build();
  }

  static void write(RunManifest manifest, Path manifestPath) throws IOException {
    try (PrintWriter writer = new PrintWriter(Files.newOutputStream(manifestPath))) {
      writer.write(LAMBDA + " " + manifest.continuation() + '\n');
      manifest.classPathFiles().forEach(cpf -> writer.write(CLASSPATH_FILE + " " + cpf + '\n'));
      manifest.files().forEach(file -> writer.write(REGULAR_FILE + " " + file + '\n'));
    }
  }
}
