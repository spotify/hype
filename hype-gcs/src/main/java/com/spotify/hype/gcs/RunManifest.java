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

import io.norberg.automatter.AutoMatter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * <pre>
 *   l continuation-ce89ba3b.bin
 *   c lib1.jar
 *   c lib2.jar
 *   c lib3.jar
 *   f other-file.txt
 * </pre>
 *
 * <p>Where the different classifiers at the beginning of the line stand for
 *
 * <pre>
 *   l continuation lambda, last one will be picked if several entries exist
 *   c jar file, will be added to the classpath
 *   f regular file, will just be downloaded to the temp location
 * </pre>
 */
@AutoMatter
public interface RunManifest {

  String continuation();

  List<String> classPathFiles();

  List<String> files();

  static RunManifest read(Path manifestPath) throws IOException {
    return ManifestUtil.read(manifestPath);
  }

  static void write(RunManifest manifest, Path manifestPath) throws IOException {
    ManifestUtil.write(manifest, manifestPath);
  }
}
