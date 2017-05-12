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
 * A request for a volume to be created from the specified storage class, with a specific size.
 *
 * <p>see http://blog.kubernetes.io/2016/10/dynamic-provisioning-and-storage-in-kubernetes.html
 */
@AutoMatter
public interface VolumeRequest {

  String VOLUME_REQUEST_PREFIX = "hype-request-";

  String id();
  boolean keep();
  NewClaimRequest spec();

  @AutoMatter
  interface NewClaimRequest {
    String storageClass();
    String size();
    boolean createIfNotExists();
  }

  static VolumeRequest volumeRequest(String storageClass, String size) {
    final String id = VOLUME_REQUEST_PREFIX + Util.randomAlphaNumeric(8);
    return new VolumeRequestBuilder()
        .id(id)
        .keep(false) // new claims are deleted by default
        .spec(new NewClaimRequestBuilder()
            .storageClass(storageClass)
            .size(size)
            .createIfNotExists(false)
            .build())
        .build();
  }

  static VolumeRequest createIfNotExists(String name, String storageClass, String size) {
    final String id = String.format("%s-%s-%s", name, storageClass, size);
    return new VolumeRequestBuilder()
        .id(id)
        .keep(true)
        .spec(new NewClaimRequestBuilder()
            .storageClass(storageClass)
            .size(size)
            .createIfNotExists(true)
            .build())
        .build();
  }

  default VolumeRequest keepOnExit() {
    return VolumeRequestBuilder.from(this)
        .keep(true)
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
