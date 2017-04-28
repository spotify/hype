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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotify.hype.model.VolumeRequest;
import io.fabric8.kubernetes.api.model.DoneablePersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VolumeRepositoryTest {

  private static final String EXISTING_CLAIM = "hype-request-abc123";

  @Rule public ExpectedException expect = ExpectedException.none();

  @Mock KubernetesClient mockClient;
  @Mock Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim> existingPvcResource;
  @Mock Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim> nonExistingPvcResource;
  @Mock PersistentVolumeClaim mockPvc;
  @Mock MixedOperation< // euw
      PersistentVolumeClaim,
      PersistentVolumeClaimList,
      DoneablePersistentVolumeClaim,
      Resource<
          PersistentVolumeClaim,
          DoneablePersistentVolumeClaim>> pvcs;

  @Captor ArgumentCaptor<PersistentVolumeClaim> createdPvc;
  @Captor ArgumentCaptor<List<PersistentVolumeClaim>> deletedPvcs;

  private VolumeRepository volumeRepository;

  @Before
  public void setUp() throws Exception {
    volumeRepository = new VolumeRepository(mockClient);
    when(mockClient.persistentVolumeClaims()).thenReturn(pvcs);
    when(pvcs.withName(any())).thenAnswer(invocation ->
        invocation.getArguments()[0].equals(EXISTING_CLAIM)
        ? existingPvcResource : nonExistingPvcResource);
    when(existingPvcResource.get()).thenReturn(mockPvc);
    when(nonExistingPvcResource.get()).thenReturn(null);
    when(pvcs.create(createdPvc.capture())).then(invocation -> createdPvc.getValue());
    when(pvcs.delete(deletedPvcs.capture())).thenReturn(true);
  }

  @Test
  public void createsNewVolumesClaim() throws Exception {
    VolumeRequest request = VolumeRequest.volumeRequest("storage-class-name", "16Gi");
    PersistentVolumeClaim claim = volumeRepository.getClaim(request);

    assertThat(claim.getMetadata().getName(), equalTo(request.id()));
    assertThat(
        claim.getMetadata().getAnnotations(),
        hasEntry(VolumeRepository.STORAGE_CLASS_ANNOTATION, "storage-class-name"));
    assertThat(claim.getSpec().getAccessModes(), contains("ReadWriteOnce", "ReadOnlyMany"));
    assertThat(
        claim.getSpec().getResources().getRequests(),
        hasEntry("storage", new Quantity("16Gi")));
  }

  @Test
  public void returnsExistingClaim() throws Exception {
    VolumeRequest request = VolumeRequest.existingClaim(EXISTING_CLAIM);
    PersistentVolumeClaim claim = volumeRepository.getClaim(request);

    assertThat(claim, is(mockPvc));
  }

  @Test
  public void throwsWhenExistingClaimNotFound() throws Exception {
    expect.expect(RuntimeException.class);
    expect.expectMessage("Requested claim 'does-not-exist' not found");

    VolumeRequest request = VolumeRequest.existingClaim("does-not-exist");
    volumeRepository.getClaim(request);
  }

  @Test
  public void cachesRequestClaims() throws Exception {
    VolumeRequest request = VolumeRequest.volumeRequest("storage-class-name", "16Gi");
    volumeRepository.getClaim(request);
    volumeRepository.getClaim(request);

    verify(pvcs, times(1)).create(any());
  }

  @Test
  public void deletesClaimsOnClose() throws Exception {
    VolumeRequest request1 = VolumeRequest.volumeRequest("storage-class-name", "16Gi").keepOnExit();
    VolumeRequest request2 = VolumeRequest.volumeRequest("storage-class-name", "16Gi");
    PersistentVolumeClaim claim1 = volumeRepository.getClaim(request1);
    PersistentVolumeClaim claim2 = volumeRepository.getClaim(request2);

    volumeRepository.close();
    assertThat(deletedPvcs.getValue(), contains(claim2));
  }
}
