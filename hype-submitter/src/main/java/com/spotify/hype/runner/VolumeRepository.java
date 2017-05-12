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

package com.spotify.hype.runner;

import static java.util.stream.Collectors.toList;

import com.spotify.hype.model.VolumeRequest;
import com.spotify.hype.model.VolumeRequest.NewClaimRequest;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repository for creating temporary {@link PersistentVolumeClaim}s from {@link VolumeRequest}s.
 *
 * <p>The repository will delete all created claims when it is closed.
 */
public class VolumeRepository implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(VolumeRepository.class);

  static final String STORAGE_CLASS_ANNOTATION = "volume.beta.kubernetes.io/storage-class";
  static final String READ_WRITE_ONCE = "ReadWriteOnce";
  static final String READ_ONLY_MANY = "ReadOnlyMany";

  private final KubernetesClient client;

  private final ConcurrentMap<VolumeRequest, PersistentVolumeClaim> claims =
      new ConcurrentHashMap<>();

  public VolumeRepository(KubernetesClient client) {
    this.client = Objects.requireNonNull(client);
  }

  PersistentVolumeClaim getClaim(VolumeRequest volumeRequest) {
    return claims.computeIfAbsent(volumeRequest, this::createClaim);
  }

  private PersistentVolumeClaim createClaim(VolumeRequest volumeRequest) {
    final NewClaimRequest spec = volumeRequest.spec();

    if (spec.createIfNotExists()) {
      final String claimName = volumeRequest.id();
      final PersistentVolumeClaim existingClaim =
          client.persistentVolumeClaims().withName(claimName).get();

      if (existingClaim != null) {
        return existingClaim;
      }
    }
    final ResourceRequirements resources = new ResourceRequirementsBuilder()
        .addToRequests("storage", new Quantity(spec.size()))
        .build();

    final PersistentVolumeClaim claimTemplate = new PersistentVolumeClaimBuilder()
        .withNewMetadata()
        .withName(volumeRequest.id())
        .addToAnnotations(STORAGE_CLASS_ANNOTATION, spec.storageClass())
        .endMetadata()
        .withNewSpec()
        // todo: storageClassName: <class> // in 1.6
        .withAccessModes(READ_WRITE_ONCE, READ_ONLY_MANY)
        .withResources(resources)
        .endSpec()
        .build();

    final PersistentVolumeClaim claim = client.persistentVolumeClaims().create(claimTemplate);
    LOG.info("Created PersistentVolumeClaim {} for {}",
        claim.getMetadata().getName(),
        volumeRequest);

    return claim;
  }

  @Override
  public void close() throws IOException {
    final List<PersistentVolumeClaim> toDelete = claims.entrySet().stream()
        .filter(e -> !e.getKey().keep())
        .map(Map.Entry::getValue)
        .collect(toList());

    client.persistentVolumeClaims().delete(toDelete);
  }
}
