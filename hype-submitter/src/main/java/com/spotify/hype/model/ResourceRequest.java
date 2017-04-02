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

import com.spotify.hype.model.Amount.Unit;
import io.norberg.automatter.AutoMatter;

/**
 * TODO: document.
 */
@AutoMatter
public interface ResourceRequest {

  ResourceRequestCreator<CpuUnit> CPU = forResource("cpu");
  ResourceRequestCreator<MemoryUnit> MEMORY = forResource("memory");

  String resource();
  Amount amount();

  static <U extends Unit> ResourceRequest request(String resource, int size, U unit) {
    return new ResourceRequestBuilder()
        .resource(resource)
        .amount(Amount.of(size, unit.toString()))
        .build();
  }

  static ResourceRequest request(String resource, int size) {
    return new ResourceRequestBuilder()
        .resource(resource)
        .amount(Amount.of(size))
        .build();
  }

  interface ResourceRequestCreator<U extends Unit> {
    ResourceRequest of(int size, U unit);
    ResourceRequest of(int size);
  }

  static <U extends Unit> ResourceRequestCreator<U> forResource(String resource) {
    return new ResourceRequestCreator<U>() {
      @Override
      public ResourceRequest of(int size, U unit) {
        return ResourceRequest.request(resource, size, unit);
      }

      @Override
      public ResourceRequest of(int size) {
        return ResourceRequest.request(resource, size);
      }
    };
  }
}
