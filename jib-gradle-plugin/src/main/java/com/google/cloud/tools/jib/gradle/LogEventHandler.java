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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.event.events.LogEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.gradle.api.logging.Logger;

/** Handles {@link LogEvent}s by passing to the Gradle {@link Logger}. */
// We don't care about the return values of the logging futures.
@SuppressWarnings("FutureReturnValueIgnored")
class LogEventHandler implements Consumer<LogEvent> {

  // TODO: This class is not needed.
  /** Keeps all log messages in a sequential, deterministic order. */
  private static class SingleThreadedLogger {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Logger logger;

    private String footer = "";
    private int footerLineCount = 0;

    private SingleThreadedLogger(Logger logger) {
      this.logger = logger;
    }

    private void accept(LogEvent logEvent) {
      switch (logEvent.getLevel()) {
        case LIFECYCLE:
          setCursorPositionAndLog(logger::lifecycle, logEvent.getMessage());
          break;

        case DEBUG:
          setCursorPositionAndLog(logger::debug, logEvent.getMessage());
          break;

        case ERROR:
          setCursorPositionAndLog(logger::error, logEvent.getMessage());
          break;

        case INFO:
          setCursorPositionAndLog(logger::info, logEvent.getMessage());
          break;

        case WARN:
          setCursorPositionAndLog(logger::warn, "warning: " + logEvent.getMessage());
          break;

        default:
          throw new IllegalStateException("Unknown LogEvent.Level: " + logEvent.getLevel());
      }
    }

    private void setCursorPositionAndLog(Consumer<String> loggerConsumer, String logMessage) {
      executorService.submit(
          () -> {
            // Moves cursor up, overwrites footer with whitespace, and then moves cursor up again.
            for (int i = 0; i < this.footerLineCount; i++) {
              // Moves cursor up.
              String eraseLine =
                  "\033[1A"
                      + "                                                                                             \r"
                      + "\033[1A";
              logger.lifecycle(eraseLine);
            }
            // TODO: overwrites footer with whitespace, and then moves cursor up again
            loggerConsumer.accept(logMessage);
            if (footerLineCount > 0) {
              logger.lifecycle(footer);
            }
          });
    }

    private void setFooter(String footer, int lineCount) {
      executorService.submit(
          () -> {
            String previousFooter = this.footer;
            int previousLineCount = this.footerLineCount;

            this.footer = footer;
            this.footerLineCount = lineCount;

            if (!footer.equals(previousFooter)) {
              // Moves cursor up, overwrites footer with whitespace, and then moves cursor up again.
              for (int i = 0; i < previousLineCount; i++) {
                // Moves cursor up.
                String eraseLine =
                    "\033[1A"
                        + "                                                                                             \r"
                        + "\033[1A";
                logger.lifecycle(eraseLine);
              }
              // TODO: overwrites footer with whitespace, and then moves cursor up again
              logger.lifecycle(footer);
            }
          });
    }
  }

  private final SingleThreadedLogger singleThreadedLogger;

  LogEventHandler(Logger logger) {
    singleThreadedLogger = new SingleThreadedLogger(logger);
  }

  @Override
  public void accept(LogEvent logEvent) {
    singleThreadedLogger.accept(logEvent);
  }

  public BiConsumer<String, Integer> getFooterSetter() {
    return singleThreadedLogger::setFooter;
  }
}
