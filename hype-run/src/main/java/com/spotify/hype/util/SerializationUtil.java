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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import com.twitter.chill.AllScalaRegistrar;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objenesis.strategy.StdInstantiatorStrategy;

public class SerializationUtil {

  private static final String CONT_FILE = "continuation-";
  private static final String EXT = ".bin";

  public static Path serializeContinuation(Fn<?> continuation) {
    try {
      final Path outputPath = Files.createTempFile(CONT_FILE, EXT);
      serializeObject(continuation, outputPath);
      return outputPath;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Fn<?> readContinuation(Path continuationPath) {
    return (Fn) readObject(continuationPath);
  }

  public static void serializeObject(Object obj, Path outputPath) {
    Kryo kryo = new Kryo();
    kryo.register(java.lang.invoke.SerializedLambda.class);
    kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
    new AllScalaRegistrar().apply(kryo);

    try {
      final File file = outputPath.toFile();
      try (Output output = new Output(new FileOutputStream(file))) {
        kryo.writeClassAndObject(output, obj);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object readObject(Path object) {
    Kryo kryo = new Kryo();
    kryo.register(java.lang.invoke.SerializedLambda.class);
    kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
    kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    new AllScalaRegistrar().apply(kryo);

    File file = object.toFile();

    try (Input input = new Input(new FileInputStream(file))) {
      return kryo.readClassAndObject(input);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
