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

package com.spotify.hype.model;

import io.norberg.automatter.AutoMatter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoMatter
public interface RunEnvironment {

  EnvironmentBase base();
  List<Secret> secretMounts();
  List<VolumeMount> volumeMounts();
  Map<String, String> resourceRequests();

  interface EnvironmentBase {
  }

  @AutoMatter
  interface SimpleBase extends EnvironmentBase {
    String image();
  }

  @AutoMatter
  interface YamlBase extends EnvironmentBase {
    Path yamlPath();
  }

  static RunEnvironment environment(String image) {
    return new RunEnvironmentBuilder()
        .base(new SimpleBaseBuilder().image(image).build())
        .build();
  }

  static RunEnvironment environment(String image, Secret secret) {
    return new RunEnvironmentBuilder()
        .base(new SimpleBaseBuilder().image(image).build())
        .addSecretMount(secret)
        .build();
  }

  static RunEnvironment fromYaml(String resourcePath, Secret secret) {
    final URI resourceUri;
    try {
      resourceUri = RunEnvironment.class.getResource(resourcePath).toURI();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return fromYaml(Paths.get(resourceUri), secret);
  }

  static RunEnvironment fromYaml(Path path, Secret secret) {
    return new RunEnvironmentBuilder()
        .base(new YamlBaseBuilder().yamlPath(path).build())
        .addSecretMount(secret)
        .build();
  }

  default RunEnvironment withSecret(Secret secret) {
    return RunEnvironmentBuilder.from(this)
        .addSecretMount(secret)
        .build();
  }

  default RunEnvironment withMount(VolumeMount volumeMount) {
    return RunEnvironmentBuilder.from(this)
        .addVolumeMount(volumeMount)
        .build();
  }

  default RunEnvironment withRequest(String resource, String amount) {
    return RunEnvironmentBuilder.from(this)
        .putResourceRequest(resource, amount)
        .build();
  }

  default RunEnvironment withRequest(ResourceRequest request) {
    return RunEnvironmentBuilder.from(this)
        .putResourceRequest(request.resource(), request.amount())
        .build();
  }
}
