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

package com.spotify.hype;

import java.io.IOException;
import java.util.Properties;

public class VersionUtil {

  public static String getVersion() {
    Properties props = new Properties();
    try {
      props.load(VersionUtil.class.getResourceAsStream("/version.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return props.getProperty("hypelet.version");
  }

}
