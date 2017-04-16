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
import com.spotify.hype.model.VolumeRequest
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import scala.language.postfixOps
import scala.sys.process._

class SubmitterOpsTest extends FlatSpec with Matchers {

  private implicit val submitter = Submitter.createLocal

  "Submitter ops" should "do #!" in {
    implicit val env = Environment(s"spotify-hype-testing:${VersionUtil.getVersion}")

    val volume = VolumeRequest("slow", "1G")

    val write = Write(volume, "foobar in a file")
    val read = Read(volume)

    (write #!) shouldBe "foobar"
    (read #!) shouldBe "foobar in a file"
  }

  it should "support explicit env" in {
    val explicitEnv = Environment(s"spotify-hype-testing:${VersionUtil.getVersion}")

    def getEnv(name: String) = HFn {
      System.getenv(name)
    }

    (getEnv("HYPE_ENV") #! explicitEnv) shouldBe "testing"
  }

  case class Write(volume: VolumeRequest, text: String) extends HFn[String] {
    def run: String = {
      // side effect in volume
      s"echo -n $text" #> "tee /foo/bar.txt" !

      "foobar"
    }

    override def env(baseEnv: RunEnvironment): RunEnvironment =
      baseEnv.withMount(volume.mountReadWrite("/foo"))
  }

  case class Read(volume: VolumeRequest) extends HFn[String] {
    def run: String =
      ("cat /readFoo/bar.txt" !!).trim

    override def env(baseEnv: RunEnvironment): RunEnvironment =
      baseEnv.withMount(volume.mountReadOnly("/readFoo"))
  }
}
