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

package com.spotify.hype.runner;

import com.spotify.hype.model.LoggingSidecar;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.StagedContinuation;
import io.norberg.automatter.AutoMatter;
import javax.annotation.Nullable;

@AutoMatter
public interface RunSpec {

  RunEnvironment runEnvironment();
  StagedContinuation stagedContinuation();
  String image();
  @Nullable
  LoggingSidecar loggingSidecar();

  static RunSpec runSpec(
      RunEnvironment runEnvironment,
      StagedContinuation stagedContinuation,
      String image,
      LoggingSidecar loggingSidecar) {
    return new RunSpecBuilder()
        .runEnvironment(runEnvironment)
        .stagedContinuation(stagedContinuation)
        .image(image)
        .loggingSidecar(loggingSidecar)
        .build();
  }
}
