/*-
 * -\-\-
 * hype-submitter
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

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Throwables;
import com.spotify.hype.util.Fn;
import com.spotify.hype.util.SerializationUtil;
import io.rouz.flo.Task;
import io.rouz.flo.TaskContext;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jhades.model.ClasspathEntry;
import org.jhades.service.ClasspathScanner;

public class Test {

  public static void main(String[] args) {
    final Storage storage = StorageOptions.getDefaultInstance().getService();
    final Submitter submitter = new Submitter(storage, args[0]);

    final List<String> files = localClasspath().stream()
        .map(entry -> Paths.get(URI.create(entry.getUrl())).toAbsolutePath().toString())
        .collect(Collectors.toList());

    final Task<String> task = Task.named("foo").ofType(String.class)
        .process(() -> "hello");

    Fn<?> fn = () -> {
      files.forEach(file -> System.out.println("running in continuation " + file));

      CompletableFuture<String> future = new CompletableFuture<>();
      TaskContext.inmem().evaluate(task).consume(future::complete);
      final String value;
      try {
        value = future.get();
      } catch (InterruptedException | ExecutionException e) {
        throw Throwables.propagate(e);
      }

      System.out.println("value = " + value);
      return value;
    };

    final Path continuationPath = SerializationUtil.serializeContinuation(fn);
    files.add(continuationPath.toAbsolutePath().toString());

    URI stagedLocation = submitter.stageFiles(files);
    System.out.println("stage args " + stagedLocation + " " + continuationPath.getFileName());
  }

  private static Set<ClasspathEntry> localClasspath() {
    final ClasspathScanner scanner = new ClasspathScanner();
    final String classLoaderName = Test.class.getClassLoader().getClass().getName();

    return scanner.findAllClasspathEntries().stream()
        .filter(entry -> classLoaderName.equals(entry.getClassLoaderName()))
        .flatMap(Test::jarFileEntriesWithExpandedManifest)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Stream<ClasspathEntry> jarFileEntriesWithExpandedManifest(ClasspathEntry entry) {
    if (!entry.isJar() || !entry.getUrl().startsWith("file:")) {
      return Stream.empty();
    }

    if (entry.findManifestClasspathEntries().isEmpty()) {
      return Stream.of(entry);
    } else {
      final URI uri = URI.create(entry.getUrl());
      Path path = Paths.get(uri).getParent();
      return Stream.concat(
          Stream.of(entry),
          entry.findManifestClasspathEntries().stream()
              .map(normalizerUsingPath(path)));
    }
  }

  private static UnaryOperator<ClasspathEntry> normalizerUsingPath(Path base) {
    return entry -> new ClasspathEntry(
        entry.getClassLoader(),
        base.resolve(entry.getUrl()).toUri().toString());
  }
}
