/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.http;

import com.google.api.client.http.HttpContent;
import com.google.cloud.tools.jib.blob.Blob;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/** {@link Blob}-backed {@link HttpContent}. */
public class BlobHttpContent implements HttpContent {

  private static class MonitoringOutputStream extends OutputStream {

    private final OutputStream underlyingOutputStream;
    private final Consumer<Long> progressMonitor;
    private long bytesWritten = 0;

    private MonitoringOutputStream(
        OutputStream underlyingOutputStream, Consumer<Long> progressMonitor) {
      this.underlyingOutputStream = underlyingOutputStream;
      this.progressMonitor = progressMonitor;
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

    private void wroteBytes(long count) {
      bytesWritten += count;
      progressMonitor.accept(bytesWritten);
    }
  }

  private final Blob blob;
  private final String contentType;
  private final Consumer<Long> progressMonitor;

  public BlobHttpContent(Blob blob, String contentType) {
    this(blob, contentType, ignored -> {});
  }

  public BlobHttpContent(Blob blob, String contentType, Consumer<Long> progressMonitor) {
    this.blob = blob;
    this.contentType = contentType;
    this.progressMonitor = progressMonitor;
  }

  @Override
  public long getLength() {
    // Returns negative value for unknown length.
    return -1;
  }

  @Override
  public String getType() {
    return contentType;
  }

  @Override
  public boolean retrySupported() {
    return false;
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    MonitoringOutputStream monitoringOutputStream =
        new MonitoringOutputStream(outputStream, progressMonitor);
    blob.writeTo(monitoringOutputStream);
    monitoringOutputStream.flush();
  }
}
