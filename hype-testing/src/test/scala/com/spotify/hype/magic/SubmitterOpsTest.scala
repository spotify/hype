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
package com.spotify.hype.magic

import com.spotify.hype.{HFn, HFnTest, LocalSubmitter, RunEnvironment, TransientVolume}
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps
import scala.sys.process._

class SubmitterOpsTest extends FlatSpec with Matchers {

  private implicit val submitter = LocalSubmitter()

  "Submitter ops" should "do #!" in {
    implicit val env = RunEnvironment()

    (getEnv("HYPE_ENV") #!) shouldBe "testing"
  }

  def getEnv(name: String): HFn[String] = HFn.withImage(HFnTest.testImage) {
    System.getenv(name)
  }

  it should "support explicit env" in {
    val volume = TransientVolume("slow", "1G")
    val explicitEnv = RunEnvironment()
    val rwEnv = explicitEnv.withMount(volume.mountReadWrite("/foo"))
    val roEnv = explicitEnv.withMount(volume.mountReadOnly("/readFoo"))

    (write("foobar in a file") #! rwEnv) shouldBe "foobar"
    (read #! roEnv) shouldBe "foobar in a file"
  }

  def write(text: String): HFn[String] = HFn {
    // side effect in volume
    s"echo -n $text" #> "tee /foo/bar.txt" !

    "foobar"
  }

  def read: HFn[String] = HFn {
    ("cat /readFoo/bar.txt" !!).trim
  }
}
