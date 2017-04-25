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
 * Created by romain on 4/24/17.
 */
@AutoMatter
public interface Volume {

  static VolumeRequest newVolumeRequest(String storageClass, String size) {
    final String id = "request-" + Util.randomAlphaNumeric(8);
    return new VolumeRequestBuilder()
        .id(id)
        .storageClass(storageClass)
        .size(size)
        .build();
  }
}
