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

import static com.spotify.hype.ContainerEngineCluster.containerEngineCluster;
import static com.spotify.hype.RunEnvironment.environment;
import static com.spotify.hype.Secret.secret;
import static com.spotify.hype.VolumeRequest.volumeRequest;

import com.spotify.hype.util.Fn;
import java.util.List;
import java.util.stream.Collectors;

public class Example {

  public static void main(String[] args) {
    final Record record = new Record("hello", 42);
    final Fn<List<String>> fn = () -> {
      String cwd = System.getProperty("user.dir");
      System.out.println("cwd = " + cwd);
      System.out.println("record = " + record);

      return System.getenv().entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .peek(System.out::println)
          .collect(Collectors.toList());
    };

    final ContainerEngineCluster cluster = containerEngineCluster(
        "datawhere-test", "us-east1-d", "hype-test");

    final Submitter submitter = Submitter.create(args[0], cluster);

    // create a volume request from a predefined storage class with name 'slow'
    final VolumeRequest request = volumeRequest("slow", "10Gi");
    final VolumeMount volumeMount = request.mountReadWrite("/usr/share/volume");

    final RunEnvironment environment = environment(
        "us.gcr.io/datawhere-test/hype-runner:5",
        secret("gcp-key", "/etc/gcloud"));

    final RunEnvironment envWithMount = environment
        .withMount(volumeMount);

    final List<String> ret = submitter.runOnCluster(fn, envWithMount);
    System.out.println("ret = " + ret);
  }

  private static class Record {
    final String foo;
    final int bar;

    private Record(String foo, int bar) {
      this.foo = foo;
      this.bar = bar;
    }

    @Override
    public String toString() {
      return "Record{" +
             "foo='" + foo + '\'' +
             ", bar=" + bar +
             '}';
    }
  }
}
