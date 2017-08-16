package com.spotify

import scala.collection.JavaConverters._

package object hype {

  object RunEnvironment {
    def apply(): model.RunEnvironment = model.RunEnvironment.environment()
  }

  object EnvironmentFromYaml {
    def apply(resourcePath: String): model.RunEnvironment =
      model.RunEnvironment.fromYaml(resourcePath)
  }

  object VolumeRequest {
    def apply(name: String, mountPath: String): model.VolumeRequest =
      model.VolumeRequest.volumeRequest(name, mountPath)
  }

  object LoggingSidecar {
    def apply(image: String, args: List[String] = List()): model.LoggingSidecar =
      model.LoggingSidecar.loggingSidecar(image, args.asJava)
  }

}
