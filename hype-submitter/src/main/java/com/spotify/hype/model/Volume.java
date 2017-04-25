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

import com.spotify.hype.util.Util;
import io.norberg.automatter.AutoMatter;

/**
 * Enables to attach Persistent Disks (new or existing) to your pod.
 */
@AutoMatter
public interface Volume {

  /**
   * Request on new volume.
   */
  static VolumeRequest newVolumeRequest(String storageClass, String size) {
    final String id = "request-" + Util.randomAlphaNumeric(8);
    return new VolumeRequestBuilder()
        .id(id)
        .storageClass(storageClass)
        .size(size)
        .build();
  }

  /**
   * Request an existing disk, based on its name.
   */
  static PersistentDisk fromPersistentDisk(String pdName) {
    return new PersistentDiskBuilder()
        .pdName(pdName)
        .build();
  }

  /**
   * Mount the requested volume in read-only mode at the specified path.
   */
  default VolumeMount mountReadOnly(String mountPath) {
    return VolumeMount.volumeMount(this, mountPath, true);
  }

  /**
   * Mount the requested volume in read-write mode at the specified path.
   */
  default VolumeMount mountReadWrite(String mountPath) {
    return VolumeMount.volumeMount(this, mountPath, false);
  }
}
