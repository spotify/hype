package com.spotify

import com.spotify.hype.model.Volume

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
      Volume.newVolumeRequest(name, mountPath)
  }

}
