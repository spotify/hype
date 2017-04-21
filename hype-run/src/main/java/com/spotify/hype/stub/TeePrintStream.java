/*
 * -\-\-
 * hype
 * --
 * Copyright (C) 2016 - 2017 Spotify AB
 *  --
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

package com.spotify.hype.stub;import java.io.PrintStream;

/**
 * Print Stream which will write to 2 separate streams.
 */
class TeePrintStream extends PrintStream {

  private final PrintStream out;

  TeePrintStream(PrintStream out1, PrintStream out2) {
    super(out1);
    this.out = out2;
  }

  public void write(byte buf[], int off, int len) {
    try {
      super.write(buf, off, len);
      out.write(buf, off, len);
    } catch (Exception ignored) {}
  }

  public void flush() {
    super.flush();
    out.flush();
  }
}
