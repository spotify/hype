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

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.Arrays;
import java.util.List;

public class Test {

  public static void main(String[] args) {
    List<String> files = Arrays.asList(
        "file:///Users/rouz/code/shed/hype/hype-submitter/target/hype-submitter-0.0.1-SNAPSHOT.jar"
    );

    Storage storage = StorageOptions.getDefaultInstance().getService();
    Submitter submitter = new Submitter(storage, "rouz-test");

    List<String> stage = submitter.stage(files);
    stage.forEach(System.out::println);
  }
}
