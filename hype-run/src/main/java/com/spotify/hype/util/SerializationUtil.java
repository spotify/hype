/*-
 * -\-\-
 * hype-run
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

package com.spotify.hype.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TODO: document.
 */
public class SerializationUtil {

  private static final String CONT_FILE = "continuation-";
  private static final String RET_FILE = "return-";
  private static final String SER = ".ser";

  public static Path serializeContinuation(Fn<?> continuation) {
    return serializeObject(continuation, CONT_FILE);
  }

  public static Path serializeReturnValue(Object value) {
    return serializeObject(value, RET_FILE);
  }

  public static Fn<?> readContinuation(Path continuationPath) {
    return (Fn<?>) readObject(continuationPath);
  }

  private static Path serializeObject(Object obj, String filePrefix) {
    try {
      final Path stateFilePath = Files.createTempFile(filePrefix, SER);
      final File file = stateFilePath.toFile();
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
        oos.writeObject(obj);
      }
      return stateFilePath;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object readObject(Path continuationPath) {
    File file = continuationPath.toFile();
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
      return ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
