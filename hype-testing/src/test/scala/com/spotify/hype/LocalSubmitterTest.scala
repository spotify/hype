/*
 * Copyright 2017 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.spotify.hype

import org.scalatest.{FlatSpec, Matchers}

import scala.reflect.io.File

class LocalSubmitterTest extends FlatSpec with Matchers {

  private val submitter = LocalSubmitter()
  private val env = RunEnvironment()

  "TestCluster" should "work" in {
    val fooHFn = HFn {
      "foobar"
    }

    submitter.submit(fooHFn, env) shouldBe "foobar"
  }

  it should "support volumes write -> read" in {

    val writeHFn = HFn.withImage(HFnTest.testImage) {
      // side effect in volume
      File("/foo/bar.txt").appendAll("foobar in a file")
      "foobar"
    }

    val volume = VolumeRequest("foo", "10G")
    submitter.submit(writeHFn, env.withMount(volume.mountReadWrite("/foo"))) shouldBe "foobar"

    val readHFn = HFn.withImage(HFnTest.testImage) {
      File("/readFoo/bar.txt").bufferedReader().readLine()
    }

    submitter.submit(readHFn, env.withMount(volume.mountReadOnly("/readFoo"))) shouldBe "foobar in a file"
  }
}
