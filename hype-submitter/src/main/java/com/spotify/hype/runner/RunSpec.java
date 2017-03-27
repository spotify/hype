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

import com.google.auto.value.AutoValue;
import com.spotify.hype.StagedContinuation;

@AutoValue
public abstract class RunSpec {

  public abstract String imageName();

  public abstract StagedContinuation stagedContinuation();

  public abstract Secret secret();

  public static RunSpec runSpec(
      String imageName,
      StagedContinuation stagedContinuation,
      Secret secret
  ) {
    return new AutoValue_RunSpec(imageName, stagedContinuation, secret);
  }

  @AutoValue
  public abstract static class Secret {

    public abstract String name();

    public abstract String mountPath();

    public static Secret secret(String name, String mountPath) {
      return new AutoValue_RunSpec_Secret(name, mountPath);
    }
  }
}
