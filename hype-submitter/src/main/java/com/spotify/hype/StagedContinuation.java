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

package com.spotify.hype;

import com.google.auto.value.AutoValue;
import java.net.URI;
import java.util.List;

@AutoValue
public abstract class StagedContinuation {

  public abstract URI stageLocation();
  public abstract List<URI> stagedFiles();
  public abstract String continuationFileName();

  public static StagedContinuation create(
      URI stageLocation,
      List<URI> stagedFiles,
      String continuationFileName) {
    return new AutoValue_StagedContinuation(stageLocation, stagedFiles, continuationFileName);
  }
}
