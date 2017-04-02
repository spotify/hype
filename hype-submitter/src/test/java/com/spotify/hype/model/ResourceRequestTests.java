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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public abstract class ResourceRequestTests<U extends Amount.Unit> {

  private final ResourceRequest.ResourceRequestCreator<U> creator;
  private final U[] unitValues;

  private final String expectedResourceName;

  ResourceRequestTests(
      ResourceRequest.ResourceRequestCreator<U> creator,
      U[] unitValues,
      String expectedResourceName) {
    this.creator = creator;
    this.unitValues = unitValues;
    this.expectedResourceName = expectedResourceName;
  }

  @Test
  public void resourceName() throws Exception {
    ResourceRequest request = creator.of(147);
    assertThat(request.resource(), equalTo(expectedResourceName));
  }

  @Test
  public void resourceNameWithUnit() throws Exception {
    ResourceRequest request = creator.of(147, unitValues[0]);
    assertThat(request.resource(), equalTo(expectedResourceName));
  }

  @Test
  public void scalarValue() throws Exception {
    ResourceRequest request = creator.of(147);
    assertThat(request.amount().asString(), equalTo("147"));
  }

  @Test
  public void unitValue() throws Exception {
    for (U unit : unitValues) {
      ResourceRequest request = creator.of(300, unit);
      assertThat(request.amount().asString(), equalTo("300" + unit.toString()));
    }
  }
}
