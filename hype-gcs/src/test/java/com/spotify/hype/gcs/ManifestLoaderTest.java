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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.spotify.hype.gcs.StagingUtil.StagedPackage;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ManifestLoaderTest {

  List<String> testFiles;
  Path stagingPath;
  String stagingLocation;

  @Before
  public void setUp() throws Exception {
    URLClassLoader cl = (URLClassLoader) StagingUtilTest.class.getClassLoader();
    testFiles = Arrays.stream(cl.getURLs())
        .map(url -> new File(toUri(url)))
        .map(File::getAbsolutePath)
        .collect(toList());

    stagingPath = Files.createTempDirectory("unit-test");
    stagingLocation = stagingPath.toUri().toString();
  }

  @Test
  public void end2endStaging() throws Exception {
    List<StagedPackage> stagedPackages =
        StagingUtil.stageClasspathElements(testFiles, stagingLocation);

    RunManifest manifest = new RunManifestBuilder()
        .continuation(stagedPackages.get(0).getName())
        .files(stagedPackages.stream().map(StagedPackage::getName).collect(toList()))
        .build();

    Path manifestFile = stagingPath.resolve("manifest.txt");
    RunManifest.write(manifest, manifestFile);

    // add one extra file to staging directory
    Files.createTempFile(stagingPath, "extra", "");
    List<Path> stagedFiles = Files.list(stagingPath).collect(toList());
    assertThat(stagedFiles.size(), is(testFiles.size() + 2)); // extra + manifest

    Path readPath = Files.createTempDirectory("unit-test");
    RunManifest downloadedManifest = ManifestLoader.downloadManifest(manifestFile, readPath);

    List<Path> readFiles = Files.list(readPath).collect(toList());
    assertThat(readFiles.size(), is(testFiles.size()));
    assertThat(downloadedManifest, is(manifest));
  }

  private static URI toUri(URL url) {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
