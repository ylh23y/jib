/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.common.util.concurrent.Futures;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Keeps all log messages in a sequential, deterministic order along with an additional footer that
 * always appears below log messages. This is intended to log both the messages and the footer to
 * the same console.
 *
 * <p>Make sure to call {@link #shutDown} when finished.
 */
public class AnsiLoggerWithFooter {

  /** ANSI escape sequence for moving the cursor up one line. */
  private static final String CURSOR_UP_SEQUENCE = "\033[1A";

  /** ANSI escape sequence for erasing to end of display. */
  private static final String ERASE_DISPLAY_BELOW = "\033[0J";

  /** ANSI escape sequence for setting all further characters to bold. */
  private static final String BOLD = "\033[1m";

  /** ANSI escape sequence for setting all further characters to not bold. */
  private static final String UNBOLD = "\033[0m";

  private static final Duration EXECUTOR_SHUTDOWN_WAIT = Duration.ofSeconds(1);

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private final Consumer<String> plainLogger;

  private List<String> footerLines = Collections.emptyList();

  public AnsiLoggerWithFooter(Consumer<String> plainLogger) {
    this.plainLogger = plainLogger;
  }

  /** Shuts down the {@link #executorService}. */
  public AnsiLoggerWithFooter shutDown() {
    executorService.shutdown();
    return this;
  }

  /** Waits for the {@link #executorService} to terminate. */
  public void awaitTermination() {
    try {
      if (!executorService.awaitTermination(
          EXECUTOR_SHUTDOWN_WAIT.getSeconds(), TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Runs {@code messageLogger} asynchronously.
   *
   * @param messageLogger the {@link Consumer} intended to synchronously log a message to the
   *     console
   * @param message the message to log with {@code messageLogger}
   * @return a {@link Future} to track completion
   */
  public Future<Void> log(Consumer<String> messageLogger, String message) {
    return executorService.submit(
        () -> {
          if (footerLines.size() > 0) {
            StringBuilder footerEraserBuilder = new StringBuilder();

            // Moves the cursor up to the start of the footer.
            // TODO: Optimize to single init.
            for (int i = 0; i < footerLines.size(); i++) {
              // Moves cursor up.
              footerEraserBuilder.append(CURSOR_UP_SEQUENCE);
            }

            // Erases everything below cursor.
            footerEraserBuilder.append(ERASE_DISPLAY_BELOW);

            plainLogger.accept(footerEraserBuilder.toString());
          }

          // Writes out logMessage and footer.
          String realMessage = message;
          if (footerLines.size() > 0) {
            realMessage = CURSOR_UP_SEQUENCE + realMessage;
          }
          messageLogger.accept(realMessage);
          for (String footerLine : footerLines) {
            plainLogger.accept(BOLD+footerLine+UNBOLD);
          }

          return null;
        });
  }

  /**
   * Sets the footer asynchronously. This will replace the previously-printed footer with the new
   * {@code newFooterLines}.
   *
   * <p>The footer is printed in <strong>bold</strong>.
   *
   * @param newFooterLines the footer, with each line as an element (no newline at end)
   * @return a {@link Future} to track completion
   */
  public Future<Void> setFooter(List<String> newFooterLines) {
    if (newFooterLines.equals(footerLines)) {
      return Futures.immediateFuture(null);
    }

    return executorService.submit(
        () -> {
          if (footerLines.size() > 0) {
            StringBuilder footerEraserBuilder = new StringBuilder();

            // Moves the cursor up to the start of the footer.
            // TODO: Optimize to single init.
            for (int i = 0; i < footerLines.size(); i++) {
              // Moves cursor up.
              footerEraserBuilder.append(CURSOR_UP_SEQUENCE);
            }

            // Erases everything below cursor.
            footerEraserBuilder.append(ERASE_DISPLAY_BELOW);

            plainLogger.accept(footerEraserBuilder.toString());
          }

          boolean isFirst = true;
          for (String newFooterLine : newFooterLines) {
            String toPrint = BOLD+newFooterLine+UNBOLD;
            if (isFirst) {
              toPrint = CURSOR_UP_SEQUENCE + newFooterLine;
              isFirst = false;
            }
            plainLogger.accept(toPrint);
          }

          footerLines = newFooterLines;

          return null;
        });
  }
}
