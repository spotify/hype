package com.spotify

import scala.language.implicitConversions

package object hype {

  object RunEnvironment {
    def apply(): model.RunEnvironment = model.RunEnvironment.environment()
  }

  object RunEnvironmentFromYaml {
    def apply(resourcePath: String): model.RunEnvironment =
      model.RunEnvironment.fromYaml(resourcePath)
  }

  object TransientVolume {
    def apply(storageClass: String, size: String): model.VolumeRequest =
      model.VolumeRequest.volumeRequest(storageClass, size)
  }

  object PersistentVolume {
    def apply(name: String, storageClass: String, size: String): model.VolumeRequest =
      model.VolumeRequest.createIfNotExists(name, storageClass, size)
  }

  implicit def fnToHfn[T](fn: util.Fn[T]): HFn[T] = HFn(fn.run())

  implicit def toHfn[A](fn: () => A): HFn[A] = HFn(fn())

  implicit def toFn[A](fn: () => A): util.Fn[A] = new util.Fn[A] {
    override def run(): A = fn()
  }

  implicit def toFnByName[A](fn: => A): util.Fn[A] = new util.Fn[A] {
    override def run(): A = fn
  }
}
