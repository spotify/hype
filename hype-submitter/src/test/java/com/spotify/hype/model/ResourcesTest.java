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

import static com.spotify.hype.model.ResourceRequest.CPU;
import static com.spotify.hype.model.ResourceRequest.MEMORY;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class ResourcesTest {

  @Test
  public void unitRequest() throws Exception {
    ResourceRequest unitRequest = ResourceRequest.request("foo", 7);

    assertThat(unitRequest.resource(), equalTo("foo"));
    assertThat(unitRequest.amount().asString(), equalTo("7"));
  }

  @Test
  public void request() throws Exception {
    ResourceRequest request = ResourceRequest.request("foo", 7, CustomUnit.B);

    assertThat(request.resource(), equalTo("foo"));
    assertThat(request.amount().asString(), equalTo("7B"));
  }

  public static class CpuResource extends ResourceRequestTests<CpuUnit> {
    public CpuResource() {
      super(CPU, CpuUnit.values(), "cpu");
    }
  }

  public static class MemoryResource extends ResourceRequestTests<MemoryUnit> {
    public MemoryResource() {
      super(MEMORY, MemoryUnit.values(), "memory");
    }
  }

  enum CustomUnit implements Amount.Unit {
    A, B
  }

  public static class CustomResource extends ResourceRequestTests<CustomUnit> {
    public CustomResource() {
      super(ResourceRequest.forResource("custom"), CustomUnit.values(), "custom");
    }
  }
}
