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

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.event.JibEvent;

import javax.annotation.Nullable;
import java.util.Optional;

public class ProgressEvent implements JibEvent {

  public static class ProgressAllocation {

    public static ProgressAllocation newProgressRoot(String description, long allocationUnits) {
      return new ProgressAllocation(description, allocationUnits, null);
    }

    /**
     * Gets the fraction of the root allocation this allocation represents.
     *
     * @return the fraction of the root allocation
     */
    private static double getFractionOfRoot(ProgressAllocation parent) {
      double fractionOfRoot = 1.0;

      ProgressAllocation parentProgressAllocation = parent;
      while (parentProgressAllocation != null) {
        fractionOfRoot /= parentProgressAllocation.allocationUnits;
        parentProgressAllocation = parentProgressAllocation.parent;
      }

      return fractionOfRoot;
    }

    private final String description;
    private final long allocationUnits;
    @Nullable
    private final ProgressAllocation parent;
    private final double fractionOfRoot;

    private ProgressAllocation(String description, long allocationUnits, @Nullable ProgressAllocation parent) {
      this.description = description;
      this.allocationUnits = allocationUnits;
      this.parent = parent;
      this.fractionOfRoot = getFractionOfRoot(parent);
    }

    public ProgressAllocation allocate(String description, long subAllocationUnits) {
      return new ProgressAllocation(description, subAllocationUnits, this);
      // TODO: Should call makeProgress(0);
    }

    public String getDescription() {
      return description;
    }

    public long getAllocationUnits() {
      return allocationUnits;
    }

    public Optional<ProgressAllocation> getParent() {
      return Optional.ofNullable(parent);
    }

    public ProgressEvent makeProgressEvent(long progressUnits) {
      return new ProgressEvent(this, progressUnits);
    }

    public double getFractionOfRoot() {
      return fractionOfRoot;
    }
  }

  private final ProgressAllocation progressAllocation;
  private final long progressUnits;

  private ProgressEvent(ProgressAllocation progressAllocation, long progressUnits) {
    this.progressAllocation = progressAllocation;
    this.progressUnits = progressUnits;
  }

  public ProgressAllocation getProgressAllocation() {
    return progressAllocation;
  }

  public long getProgressUnits() {
    return progressUnits;
  }
}
/**
 Overall progress split into defined chunks
 Subprogresses claim chunks as they come, but amount within each chunk unknown
 Amount within each subprogress is determined when that subprogress runs
 */