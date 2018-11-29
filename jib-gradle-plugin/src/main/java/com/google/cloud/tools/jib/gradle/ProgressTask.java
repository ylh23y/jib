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

import com.google.cloud.tools.jib.event.events.ProgressEvent.ProgressAllocation;
import com.google.cloud.tools.jib.plugins.common.ProgressMonitor;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class ProgressTask extends DefaultTask {

  @TaskAction
  public void showProgress() throws InterruptedException {
    System.out.println();

    ProgressMonitor progressMonitor = new ProgressMonitor();

    ProgressAllocation progressAllocation = ProgressAllocation.newProgressRoot("root", 2);
    progressMonitor.receiveProgressEvent(progressAllocation.makeProgressEvent(0));

    ProgressAllocation half1 = progressAllocation.allocate("half1", 100);

    while (true) {
      progressMonitor.receiveProgressEvent(half1.makeProgressEvent(1));

      Thread.sleep(1000);
    }
  }
}
