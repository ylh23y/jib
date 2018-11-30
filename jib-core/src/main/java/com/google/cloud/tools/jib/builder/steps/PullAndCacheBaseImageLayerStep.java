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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.event.events.ProgressEvent.ProgressAllocation;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Pulls and caches a single base image layer. */
class PullAndCacheBaseImageLayerStep implements AsyncStep<CachedLayer>, Callable<CachedLayer> {

  private static final String DESCRIPTION = "Pulling base image layer %s";

  private final BuildConfiguration buildConfiguration;
  private final DescriptorDigest layerDigest;
  @Nullable private final Authorization pullAuthorization;
  private final ProgressAllocation parentProgressAllocation;

  private final ListenableFuture<CachedLayer> listenableFuture;

  PullAndCacheBaseImageLayerStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      DescriptorDigest layerDigest,
      @Nullable Authorization pullAuthorization,
      ProgressAllocation parentProgressAllocation) {
    this.buildConfiguration = buildConfiguration;
    this.layerDigest = layerDigest;
    this.pullAuthorization = pullAuthorization;
    this.parentProgressAllocation = parentProgressAllocation;

    listenableFuture = listeningExecutorService.submit(this);
  }

  @Override
  public ListenableFuture<CachedLayer> getFuture() {
    return listenableFuture;
  }

  @Override
  public CachedLayer call() throws IOException, CacheCorruptedException {
    // TODO: Refactor progress updates.
    ProgressAllocation progressAllocation =
        parentProgressAllocation.allocate("pull base image layer " + layerDigest, 1);
    buildConfiguration.getEventDispatcher().dispatch(progressAllocation.makeProgressEvent(0));

    try (TimerEventDispatcher ignored =
        new TimerEventDispatcher(
            buildConfiguration.getEventDispatcher(), String.format(DESCRIPTION, layerDigest))) {
      Cache cache = buildConfiguration.getBaseImageLayersCache();

      // Checks if the layer already exists in the cache.
      Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
      if (optionalCachedLayer.isPresent()) {
        // TODO: REFACTOR
        buildConfiguration.getEventDispatcher().dispatch(progressAllocation.makeProgressEvent(1));
        return optionalCachedLayer.get();
      }

      RegistryClient registryClient =
          buildConfiguration
              .newBaseImageRegistryClientFactory()
              .setAuthorization(pullAuthorization)
              .newRegistryClient();

      // TODO: This is such a hack.
      List<ProgressAllocation> pullProgressAllocation = new ArrayList<>();
      CachedLayer cachedLayer =
          cache.writeCompressedLayer(
              registryClient.pullBlob(
                  layerDigest,
                  layerSize -> {
                    if (pullProgressAllocation.size() > 0) {
                      throw new IllegalStateException("WHAT");
                    }
                    pullProgressAllocation.add(
                        progressAllocation.allocate("pulling layer " + layerDigest, layerSize));
                  },
                  bytesReceived -> {
                    buildConfiguration
                        .getEventDispatcher()
                        .dispatch(pullProgressAllocation.get(0).makeProgressEvent(bytesReceived));
                  }));

      return cachedLayer;
    }
  }
}
