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

@AutoMatter
public interface Amount {

  int size();
  String unit();

  static Amount of(int size, String unit) {
    return new AmountBuilder().size(size).unit(unit).build();
  }

  static Amount of(int size) {
    return new AmountBuilder().size(size).unit("").build();
  }

  default String asString() {
    return String.format("%d%s", size(), unit());
  }

  interface Unit {
  }
}
