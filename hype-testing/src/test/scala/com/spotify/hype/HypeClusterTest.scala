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

import com.spotify.hype.model.RunEnvironment
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps
import scala.reflect.io.File

class HypeClusterTest extends FlatSpec with Matchers {

  private val cluster = TestCluster()
  private val env = RunEnvironment.get()

  val fooHFn = fnWithTestImage(() => "foobar")

  "TestCluster" should "work" in {
    cluster.submit(fooHFn, env) shouldBe "foobar"
  }

  it should "support volumes write -> read" in {

    val writeHFn = fnWithTestImage(() => {
      // side effect in volume
      File("/foo/bar.txt").appendAll("foobar in a file")
      "foobar"
    })

    val volume = VolumeRequest("foo", "10G")
    cluster.submit(writeHFn, env.withMount(volume.mountReadWrite("/foo"))) shouldBe "foobar"

    val readHFn = fnWithTestImage(() => File("/readFoo/bar.txt").bufferedReader().readLine())

    cluster.submit(readHFn, env.withMount(volume.mountReadOnly("/readFoo"))) shouldBe "foobar in a file"
  }

  private def fnWithTestImage[T](fn: () => T) = new HFn[T] {
    override def run = fn()

    override def image = s"spotify-hype-testing:${VersionUtil.getVersion}"
  }
}
