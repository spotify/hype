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

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import scala.language.postfixOps
import scala.reflect.io.File

class LocalSubmitterTest extends FlatSpec with Matchers {

  private implicit val submitter = Submitter.createLocal
  private implicit val testEnv = Environment(s"spotify-hype-testing:${VersionUtil.getVersion}")

  "LocalSubmitter" should "work" in {
    (HFn("foobar") #!) shouldBe "foobar"
  }

  it should "support volumes write -> read" in {
    val volume = VolumeRequest("foo", "10G")

    val writeEnv = testEnv.withMount(volume.mountReadWrite("/foo"))
    val writeFn = () => {
      // side effect in volume
      File("/foo/bar.txt").appendAll("foobar in a file")
      "foobar"
    }

    val readEnv = testEnv.withMount(volume.mountReadOnly("/readFoo"))
    val readFn = () => File("/readFoo/bar.txt").bufferedReader().readLine()

    writeFn #! writeEnv shouldBe "foobar"
    readFn #! readEnv shouldBe "foobar in a file"
  }
}
