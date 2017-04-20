package com.spotify.hype

import org.scalatest.{FlatSpec, Matchers}

object HFnTest {
  val testImage = s"spotify-hype-testing:${VersionUtil.getVersion}"
}

class HFnTest extends FlatSpec with Matchers {

  "HypeModule" should "capture method arguments" in {
    def fn(arg: String) = HFn[String] {
      arg + " world"
    }

    roundtrip(fn("hello")) shouldBe "hello world"
  }

  it should "not evaluate on construction" in {
    var called = false
    def fn(arg: String) = HFn[String] {
      called = true
      arg + " world"
    }

    val result = roundtrip(fn("hello"))

    called shouldBe false
    result shouldBe "hello world"
    called shouldBe false
  }

  it should "convert function0 to module" in {
    var called = false
    val fn = () => {
      called = true
      "hello world"
    }

    called shouldBe false
    roundtrip(fn) shouldBe "hello world"
    called shouldBe false
  }

  /**
    * Do a roundtrip through the serializer before running the module function. This should
    * ensure that the syntax used in the tests will survive the closure cleaner.
    */
  def roundtrip[T](module: HFn[T]): T = {
    Wrapper.roundtrip(module.run).run()
  }
}
