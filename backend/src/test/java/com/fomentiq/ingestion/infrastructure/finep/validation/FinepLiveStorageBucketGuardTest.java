package com.fomentiq.ingestion.infrastructure.finep.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class FinepLiveStorageBucketGuardTest {
    private final FinepLiveStorageBucketGuard guard = new FinepLiveStorageBucketGuard();

    @Test
    void permitsOnlyConfirmedNotFoundAndRejectsExistingOrAmbiguousBucket() {
        S3Client absent = mock(S3Client.class);
        when(absent.headBucket(org.mockito.ArgumentMatchers.any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());
        guard.requireAbsent(absent, "fomentiq-live-uuid");

        S3Client present = mock(S3Client.class);
        when(present.headBucket(org.mockito.ArgumentMatchers.any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        assertThatThrownBy(() -> guard.requireAbsent(present, "fomentiq-live-uuid"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Validation storage bucket already exists");

        S3Client ambiguous = mock(S3Client.class);
        when(ambiguous.headBucket(org.mockito.ArgumentMatchers.any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("forbidden").build());
        assertThatThrownBy(() -> guard.requireAbsent(ambiguous, "fomentiq-live-uuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Validation storage bucket absence could not be verified");
    }
}
