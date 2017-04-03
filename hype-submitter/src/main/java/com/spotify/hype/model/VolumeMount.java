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

/**
 * A binding for a {@link VolumeRequest} to be used at a specified mount path.
 */
@AutoMatter
public interface VolumeMount {

  VolumeRequest volumeRequest();
  String mountPath();
  boolean readOnly();

  static VolumeMount volumeMount(
      VolumeRequest volumeRequest, String mountPath, boolean readOnly) {
    return new VolumeMountBuilder()
        .volumeRequest(volumeRequest)
        .mountPath(mountPath)
        .readOnly(readOnly)
        .build();
  }
}
