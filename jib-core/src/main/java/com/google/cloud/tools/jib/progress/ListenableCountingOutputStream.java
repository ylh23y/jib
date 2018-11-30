/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.progress;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class ListenableCountingOutputStream extends OutputStream {

  private final OutputStream underlyingOutputStream;
  private final Consumer<Long> byteCountConsumer;
  private long bytesWritten = 0;

  public ListenableCountingOutputStream(
      OutputStream underlyingOutputStream, Consumer<Long> byteCountConsumer) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.byteCountConsumer = byteCountConsumer;
  }

  @Override
  public void write(int b) throws IOException {
    underlyingOutputStream.write(b);
    wroteBytes(1);
  }

  @Override
  public void write(byte[] b) throws IOException {
    underlyingOutputStream.write(b);
    wroteBytes(b.length);
  }

  @Override
  public void write(byte b[], int off, int len) throws IOException {
    underlyingOutputStream.write(b, off, len);
    wroteBytes(len);
  }

  @Override
  public void flush() throws IOException {
    underlyingOutputStream.flush();
  }

  @Override
  public void close() throws IOException {
    underlyingOutputStream.close();
  }

  // TODO: Might not be needed.
  public long getBytesWritten() {
    return bytesWritten;
  }

  private void wroteBytes(long count) {
    bytesWritten += count;
    byteCountConsumer.accept(count);
  }
}
