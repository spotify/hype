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

import static com.spotify.hype.model.ContainerEngineCluster.containerEngineCluster;
import static com.spotify.hype.model.ResourceRequest.CPU;
import static com.spotify.hype.model.ResourceRequest.MEMORY;
import static com.spotify.hype.model.VolumeRequest.volumeRequest;

import com.spotify.hype.model.ContainerEngineCluster;
import com.spotify.hype.model.RunEnvironment;
import com.spotify.hype.model.VolumeRequest;
import com.spotify.hype.util.Fn;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Example {

  public static void main(String[] args) throws Exception {
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

    String image = "spotify/hype";
    final RunEnvironment environment = RunEnvironment.get()
        .withSecret("gcp-key", "/etc/gcloud")
        .withRequest(CPU.of("200m"))
        .withRequest(MEMORY.of("256Mi"));

    // create a volume request from a predefined storage class with name 'slow'
    final VolumeRequest slow10Gi = volumeRequest("slow", "10Gi");

    final RunEnvironment rwEnv =
        environment.withMount(slow10Gi.mountReadWrite("/usr/share/volume"));
    final RunEnvironment roEnv = environment.withMount(slow10Gi.mountReadOnly("/usr/share/volume"));

    final ContainerEngineCluster cluster = containerEngineCluster(
        "datawhere-test", "us-east1-d", "hype-test");

    try (final Submitter submitter = Submitter.create(args[0], cluster)) {
      final List<String> ret = submitter.runOnCluster(fn, rwEnv, image);
      System.out.println("ret = " + ret);

      IntStream.range(0, 10)
          .parallel()
          .forEach(i -> submitter.runOnCluster(fn, roEnv, image));
    }
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
