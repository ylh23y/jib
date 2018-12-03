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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.ProgressEvent.ProgressAllocation;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ProgressMonitor implements Consumer<ProgressEvent> {

  private final BiConsumer<String, Integer> progressLineConsumer;

  /** Maps from {@link ProgressAllocation} to number of units completed in that allocation. */
  private final Map<ProgressAllocation, Long> allocationCompletionMap = new HashMap<>();

  private double progress = 0.0;
  private String executingTasks = "";
  private int executingTasksLineCount = 0;

  public ProgressMonitor(BiConsumer<String, Integer> progressLineConsumer) {
    this.progressLineConsumer = progressLineConsumer;
  }

  @Override
  public synchronized void accept(ProgressEvent progressEvent) {
    ProgressAllocation progressAllocation = progressEvent.getProgressAllocation();
    long progressUnits = progressEvent.getProgressUnits();
    long progressTotal = progressAllocation.getAllocationUnits();

    boolean tasksUpdated = updateCompletionMap(progressAllocation, progressUnits);

    // TODO: Refactor
    if (progressUnits == 0) {
      return;
    }

    progress += progressUnits * progressAllocation.getFractionOfRoot() / progressTotal;

    displayProgress(50, tasksUpdated);
  }

  private boolean updateCompletionMap(ProgressAllocation progressAllocation, long progressUnits) {
    boolean tasksUpdated = false;

    if (!allocationCompletionMap.containsKey(progressAllocation)) {
      allocationCompletionMap.put(progressAllocation, 0L);
      tasksUpdated = true;
    }
    if (progressUnits == 0) {
      return false;
    }

    long priorCompleted = allocationCompletionMap.get(progressAllocation);
    long newCompleted = priorCompleted + progressUnits;
    if (newCompleted > progressAllocation.getAllocationUnits()) {
      throw new IllegalStateException("Progress exceeds max for '" + progressAllocation.getDescription() + "': " + newCompleted + " > " + progressAllocation.getAllocationUnits());
    }
    allocationCompletionMap.put(progressAllocation, newCompleted);

    // Updates the parents.
    if (newCompleted == progressAllocation.getAllocationUnits()) {
      progressAllocation.getParent().ifPresent(parentProgressAllocation -> updateCompletionMap(parentProgressAllocation, 1));
      return true;
    }

    return tasksUpdated;
  }

//  private List<String> getUnfinishedDescriptions() {
//    List<String> descriptions = new ArrayList<>();
//    for (Map.Entry<ProgressAllocation, Long> allocationCompletionEntry : allocationCompletionMap.entrySet()) {
//      ProgressAllocation progressAllocation = allocationCompletionEntry.getKey();
//      if (progressAllocation.getAllocationUnits() != allocationCompletionEntry.getValue()) {
//        descriptions.add(progressAllocation.getDescription());
//      }
//    }
//    return descriptions;
//  }

  private void displayProgress(int numBars, boolean tasksUpdated) {
    StringBuilder progressLine = new StringBuilder();
    int lineCount = 0;

    progressLine.append('\n');
    lineCount ++;

    if (tasksUpdated) {
      StringBuilder executingTasksBuilder = new StringBuilder();
      executingTasksLineCount = 0;

      // First, get all progress allocation that have not completed.
      // Then, go through all and make set of progress allocation that are not leaf.
      // Then, prune not completed to leave only leaf.

      Set<ProgressAllocation> notCompletedAll = new HashSet<>();

      for (Map.Entry<ProgressAllocation, Long> allocationCompletionEntry : allocationCompletionMap.entrySet()) {
        ProgressAllocation progressAllocation = allocationCompletionEntry.getKey();
        long progressUnits = allocationCompletionEntry.getValue();

        if (progressUnits < progressAllocation.getAllocationUnits()) {
          notCompletedAll.add(progressAllocation);
        }
      }

      Set<ProgressAllocation> isLeaf = new HashSet<>();
      for (ProgressAllocation progressAllocation : notCompletedAll) {
        isLeaf.add(progressAllocation);
      }
      for (ProgressAllocation progressAllocation : notCompletedAll) {
        Optional<ProgressAllocation> parent = progressAllocation.getParent();

        while (parent.isPresent()) {
          isLeaf.remove(parent.get());
          parent = parent.get().getParent();
        }
      }

      for (ProgressAllocation progressAllocation : isLeaf) {
        executingTasksBuilder.append("> ").append(progressAllocation.getDescription()).append('\n');
        executingTasksLineCount ++;
      }

      executingTasks = executingTasksBuilder.toString();
    }

    if (executingTasksLineCount > 0) {
      progressLine.append("Executing tasks:\n").append(executingTasks);
      lineCount += executingTasksLineCount + 1;
    }

    progressLine.append("[");
    int barsToDisplay = (int)Math.round(numBars * progress);
    for (int i = 0; i < numBars; i ++) {
      if (i < barsToDisplay) {
        progressLine.append('=');
      } else {
        progressLine.append(' ');
      }
    }
    progressLine.append("] ");
    progressLine.append(String.format("%.1f", progress * 100));
    progressLine.append("% complete");
    lineCount ++;

    progressLine.append('\n');
    lineCount ++;

    progressLineConsumer.accept(progressLine.toString(), lineCount);
  }
}
