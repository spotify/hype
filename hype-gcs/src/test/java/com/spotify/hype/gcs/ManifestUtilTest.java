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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ManifestUtilTest {

  private static final RunManifest EXAMPLE = new RunManifestBuilder()
      .continuation("continuation-ce89ba3b.bin")
      .classPathFiles("lib1.jar", "lib2.jar", "lib3.jar")
      .files("other-file.txt")
      .build();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void readManifest() throws Exception {
    Path manifestPath = load("/example-manifest.txt");
    RunManifest manifest = ManifestUtil.read(manifestPath);

    assertThat(manifest, is(EXAMPLE));
  }

  @Test
  public void multipleContinuation() throws Exception {
    Path manifestPath = load("/multi-lambda-manifest.txt");
    RunManifest manifest = ManifestUtil.read(manifestPath);

    assertThat(manifest.continuation(), is("continuation-other.bin"));
  }

  @Test
  public void writeManifest() throws Exception {
    Path manifest = Files.createTempFile("manifest", ".txt");
    ManifestUtil.write(EXAMPLE, manifest);

    List<String> expected = Files.readAllLines(load("/example-manifest.txt"));
    List<String> strings = Files.readAllLines(manifest);

    assertThat(strings, is(expected));
  }

  @Test
  public void skipEmptyLines() throws Exception {
    Path manifestPath = load("/empty-lines-manifest.txt");
    RunManifest manifest = ManifestUtil.read(manifestPath);

    assertThat(manifest, is(EXAMPLE));
  }

  @Test
  public void malformedManifest() throws Exception {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Malformed manifest line 'clib2.jar'");

    Path manifestPath = load("/malformed-manifest.txt");
    ManifestUtil.read(manifestPath);
  }

  private Path load(String resourceName) throws URISyntaxException {
    URL resource = ManifestUtil.class.getResource(resourceName);
    return Paths.get(resource.toURI());
  }
}
