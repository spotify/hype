/*-
 * -\-\-
 * hype-testing
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

package com.spotify.hype

import java.util.function.Consumer

import com.spotify.hype.util.Fn
import com.spotify.hype.util.SerializationUtil._
import io.rouz.flo._
import org.scalatest._

class ScalaSerializationTest extends FlatSpec with Matchers {

  "A flo task closure" can "be serialized and deserialized" in {
    val ret = closure("do")

    TaskContext.inmem().evaluate(ret).consume(new Consumer[String] {
      def accept(t: String): Unit = {
        t shouldBe "do more"
      }
    })
  }

  def closure(arg: String): Task[String] = defTask($
    in      inner
    process Wrapper.wrap(ExampleFunction(arg))
  )

  def inner: Task[String] = task("inner").process("more")
}

object ExampleFunction {
  def apply(arg: String): (String) => String = {
    (other) => arg + " " + other
  }
}

object Wrapper {

  def wrap(fn: (String) => String): (String) => String = {
    (a) => {
      val fnn = fn
      val closure = tofn(fnn(a))

      val deserialized = roundtrip(closure)
      deserialized.run()
    }
  }

  def roundtrip[T](fn: Fn[T]): Fn[T] =
    readContinuation(serializeContinuation(fn)).asInstanceOf[Fn[T]]

  def tofn[A](continuation: => A): Fn[A] = new Fn[A] {
    def run(): A = continuation
  }
}
