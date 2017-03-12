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

public class SerializationUtil {

  private static final String CONT_FILE = "continuation-";
  private static final String SER = ".ser";

  public static Path serializeContinuation(Fn<?> continuation) {
    try {
      final Path outputPath = Files.createTempFile(CONT_FILE, SER);
      serializeObject(continuation, outputPath);
      return outputPath;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Fn<?> readContinuation(Path continuationPath) {
    return (Fn<?>) readObject(continuationPath);
  }

  public static void serializeObject(Object obj, Path outputPath) {
    try {
      final File file = outputPath.toFile();
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
        oos.writeObject(obj);
      }
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
